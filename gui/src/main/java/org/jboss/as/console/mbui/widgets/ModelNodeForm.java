package org.jboss.as.console.mbui.widgets;

import org.jboss.ballroom.client.rbac.SecurityContext;
import org.jboss.ballroom.client.widgets.forms.AbstractForm;
import org.jboss.ballroom.client.widgets.forms.EditListener;
import org.jboss.ballroom.client.widgets.forms.FormItem;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.ModelType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Heiko Braun
 * @date 11/12/12
 */
public class ModelNodeForm extends AbstractForm<ModelNode> {

    private final String address;
    private final SecurityContext securityContext;
    private ModelNode editedEntity = null;
    private Map<String, ModelNode> defaults = Collections.EMPTY_MAP;
    private boolean hasWritableAttributes;

    public ModelNodeForm(String address, SecurityContext securityContext) {
        this.address = address;
        this.securityContext = securityContext;
    }

    @Override
    public void editTransient(ModelNode newBean) {
        isTransient = true;
        edit(newBean);
    }

    @Override
    public void edit(ModelNode bean) {

        // Needs to be declared (i.e. when creating new instances)
        if(null==bean)
            throw new IllegalArgumentException("Invalid entity: null");

        this.editedEntity = bean;

        final Map<String, String> exprMap = getExpressions(editedEntity);

        //final List<ModelNode> filteredDMRNames = bean.hasDefined("_filtered-attributes") ?
        //        bean.get("_filtered-attributes").asList() : Collections.EMPTY_LIST;

        // visit form
        ModelNodeInspector inspector = new ModelNodeInspector(bean);
        inspector.accept(new ModelNodeVisitor()
        {

            private boolean isComplex = false;

            @Override
            public boolean visitValueProperty(
                    final String propertyName, final ModelNode value, final PropertyContext ctx) {

                if(isComplex ) return true; // skip complex types

                visitItem(propertyName, new FormItemVisitor() {

                    public void visit(FormItem item) {

                        item.resetMetaData();

                        // expressions
                        String exprValue = exprMap.get(propertyName);
                        if(exprValue!=null)
                        {
                            item.setUndefined(false);
                            item.setExpressionValue(exprValue);
                        }

                        // values
                        else if(value.isDefined()) {
                            item.setUndefined(false);
                            item.setValue(downCast(value));
                        }
                        else if(defaults.containsKey(propertyName))
                        {
                            item.setUndefined(false);
                            item.setValue(downCast(defaults.get(propertyName)));
                        }
                        else
                        {
                            // when no value is given we still need to validate the input
                            item.setUndefined(true);
                            item.setModified(true); // don't escape validation
                        }

                        // RBAC: attribute level constraints

                        /*for(ModelNode att : filteredDMRNames)
                        {
                            if(att.asString().equals(propertyName))
                            {
                                item.setFiltered(true);
                                break;
                            }
                        } */
                    }
                });

                return true;
            }

            @Override
            public boolean visitReferenceProperty(String propertyName, ModelNode value, PropertyContext ctx) {
                isComplex = true;
                return true;
            }

            @Override
            public void endVisitReferenceProperty(String propertyName, ModelNode value, PropertyContext ctx) {
                isComplex = false;
            }

            @Override
            public boolean visitCollectionProperty(String propertyName, final ModelNode value, PropertyContext ctx) {
                visitItem(propertyName, new FormItemVisitor() {

                    public void visit(FormItem item) {

                        item.resetMetaData();

                        if(value!=null)
                        {
                            item.setUndefined(false);
                            //TODO: item.setValue(value.asList());
                            item.setValue(Collections.EMPTY_LIST);
                        }
                        else
                        {
                            item.setUndefined(true);
                            item.setModified(true); // don't escape validation
                        }
                    }
                });

                return true;
            }
        });

        // plain views
        refreshPlainView();
    }

    /**
     * The MBUI kernel provides the context
     * @return
     */
    @Override
    protected SecurityContext getSecurityContext() {
        return this.securityContext;
    }

    @Override
    public Set<String> getReadOnlyNames() {

        Set<String> readOnly = new HashSet<String>();
        for(String item : getFormItemNames())
        {
            if(!securityContext.getAttributeWritePriviledge(item).isGranted())
                readOnly.add(item);
        }
        return readOnly;
    }

    @Override
    public Set<String> getFilteredNames() {
        Set<String> filtered = new HashSet<String>();
        for(String item : getFormItemNames())
        {
            boolean writePriv = securityContext.getAttributeWritePriviledge(item).isGranted();
            boolean readPriv = securityContext.getAttributeReadPriviledge(item).isGranted();
            if(!writePriv && !readPriv)
                filtered.add(item);
        }
        return filtered;
    }


    public static Object downCast(ModelNode value)
    {
        Object result = null;
        ModelType type = value.getType();
        switch (type)
        {
            case STRING:
                result = value.asString();
                break;
            case INT:
                result = value.asInt();
                break;
            case LONG:
                result = value.asLong();
                break;
            case BOOLEAN:
                result = value.asBoolean();
                break;
            case BIG_DECIMAL:
                result = value.asBigDecimal();
                break;
            case BIG_INTEGER:
                result = value.asBigInteger();
                break;
            case DOUBLE:
                result = value.asDouble();
                break;
            case LIST: {

                List<ModelNode> items = value.asList();
                List<String> list = new ArrayList<String>(items.size());
                for(ModelNode item : items)
                    list.add(item.asString()); // TODO: currently the only supported type
                result = list;
                break;
            }
            case UNDEFINED:
                break;
            default:
                throw new RuntimeException("Unexpected type "+type);

        }
        return result;
    }

    void visitItem(final String name, FormItemVisitor visitor) {
        String namePrefix = name + "_";
        for(Map<String, FormItem> groupItems : formItems.values())
        {
            for(String key : groupItems.keySet())
            {
                if(key.equals(name) || key.startsWith(namePrefix))
                {
                    visitor.visit(groupItems.get(key));
                }
            }
        }
    }

    private Map<String, String> getExpressions(ModelNode bean) {
        final Map<String, String> exprMap = new HashMap<String,String>();

        // parse expressions
        ModelNodeInspector inspector = new ModelNodeInspector(bean);
        inspector.accept(new ModelNodeVisitor()
        {
            @Override
            public boolean visitValueProperty(String propertyName, ModelNode value, PropertyContext ctx) {
                if(value.getType() == ModelType.EXPRESSION)
                {
                    exprMap.put(propertyName, value.asString());
                }
                return true;
            }
        });

        bean.setTag(EXPR_TAG, exprMap);

        return exprMap;
    }

    @Override
    public void cancel() {
        //clearValues();
        if(editedEntity!=null && editedEntity.isDefined()) edit(editedEntity);
    }

    @Override
    public Map<String, Object> getChangedValues() {

        final Map<String,Object> changedValues = new HashMap<String, Object>();

        ModelNodeInspector inspector = new ModelNodeInspector(this.getUpdatedEntity());
        inspector.accept(new ModelNodeVisitor()
                         {
                             @Override
                             public boolean visitValueProperty(String propertyName, ModelNode value, PropertyContext ctx) {
                                 ModelNode src = ModelNodeForm.this.editedEntity;
                                 ModelNode dest = getUpdatedEntity();

                                 if(!src.get(propertyName).equals(dest.get(propertyName)))
                                     changedValues.put(propertyName, downCast(dest.get(propertyName)));

                                 return true;
                             }
                         }
        );

        Map<String, Object> finalDiff = new HashMap<String,Object>();

        // map changes, but skip unmodified fields
        for(Map<String, FormItem> groupItems : formItems.values())
        {
            for(FormItem item : groupItems.values())
            {
                Object val = changedValues.get(item.getName());

                // expression have precedence over real values
                if(item.isExpressionValue())
                {
                    finalDiff.put(item.getName(), item.asExpressionValue());
                }

                // regular values
                else if(val!=null && item.isModified())
                {
                    if(item.isUndefined())
                        finalDiff.put(item.getName(), FormItem.VALUE_SEMANTICS.UNDEFINED);
                    else
                        finalDiff.put(item.getName(), val);
                }
            }
        }

        return finalDiff;

    }

    @Override
    public ModelNode getUpdatedEntity() {

        final ModelNode updatedModel = getEditedEntity()==null ?
                new ModelNode() : getEditedEntity().clone();

        for(Map<String, FormItem> groupItems : formItems.values())
        {
            for(String key : groupItems.keySet())
            {
                visitItem(key, new FormItemVisitor() {
                    @Override
                    public void visit(FormItem item) {

                        ModelNode node = updatedModel.get(item.getName());
                        Object obj = item.getValue();
                        Class baseType = obj.getClass();

                        // STRING
                        if (baseType == String.class) {
                            String stringValue = (String) obj;
                            if(stringValue.startsWith("$"))
                                node.setExpression(stringValue);
                            else if("".equals(stringValue))
                                node.clear(); // TODO: depends on nillable?
                            else
                                node.set(stringValue);
                        }

                        // Numeric Values
                        else if (baseType == Long.class) {
                            Long longValue = (Long) obj;
                            if(0 == longValue)
                                node.clear();
                            else
                                node.set(longValue);
                        } else if (baseType == Integer.class) {
                            Integer intValue = (Integer) obj;
                            if(0 == intValue)
                                node.clear();
                            else
                                node.set(intValue);
                        } else if (baseType == BigDecimal.class) {
                            BigDecimal bigValue = (BigDecimal) obj;
                            if(0.00 == bigValue.doubleValue())
                                node.clear();
                            else
                                node.set(bigValue);
                        } else if (baseType == Double.class) {
                            Double dValue = (Double) obj;
                            if(0.00 == dValue)
                                node.clear();
                            else
                                node.set(dValue);
                        }

                        // BOOL
                        else if (baseType == Boolean.class) {
                            node.set((Boolean)obj);
                        }

                        // BYTE
                        else if (baseType == byte[].class) {
                            node.set((byte[]) obj);
                        }

                        // LIST
                        else if (baseType == ArrayList.class) {
                            node.clear();
                            List l = (List)obj;
                            for(Object o : l)
                                node.add(o.toString()); // TODO: type conversion ?

                        }

                        else {
                            throw new IllegalArgumentException("Can not convert. This value is not of a recognized base type. Value =" + obj.toString());
                        }
                    }
                });
            }
        }

        return updatedModel;
    }

    @Override
    public ModelNode getEditedEntity() {
        return editedEntity;
    }

    @Override
    public void clearValues() {

        editedEntity = null;

        for(Map<String, FormItem> groupItems : formItems.values())
        {
            for(String key : groupItems.keySet())
            {
                visitItem(key, new FormItemVisitor() {
                    @Override
                    public void visit(FormItem item) {
                        item.clearValue();
                    }
                });
            }
        }

        refreshPlainView();
    }

    public void setDefaults(Map<String, ModelNode> defaults) {
        this.defaults = defaults;
    }

    public boolean hasWritableAttributes() {
        return hasWritableAttributes;
    }

interface FormItemVisitor {
    void visit(FormItem item);
}


    // ---- deprecated, blow up -----

    @Override
    public Class<?> getConversionType() {
        throw new RuntimeException("API Incompatible: getConversionType() not supported on "+getClass().getName());
    }

    @Override
    public void addEditListener(EditListener listener) {
        throw new RuntimeException("API Incompatible: addEditListener() not supported on "+getClass().getName());
    }

    @Override
    public void removeEditListener(EditListener listener) {
        throw new RuntimeException("API Incompatible: removeEditListener() not supported on "+getClass().getName());
    }

    public void setHasWritableAttributes(boolean hasWritableAttributes) {
        this.hasWritableAttributes = hasWritableAttributes;
    }


}
