package org.jboss.as.console.client.domain.hosts;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionModel;
import com.google.gwt.view.client.SingleSelectionModel;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.domain.model.Host;
import org.jboss.as.console.client.domain.model.ServerInstance;
import org.jboss.as.console.client.v3.stores.domain.actions.HostSelection;
import org.jboss.as.console.client.v3.stores.domain.actions.SelectServerInstance;
import org.jboss.as.console.client.widgets.lists.DefaultCellList;
import org.jboss.as.console.client.widgets.popups.DefaultPopup;
import org.jboss.ballroom.client.widgets.common.DefaultButton;
import org.jboss.ballroom.client.widgets.tables.DefaultPager;
import org.jboss.ballroom.client.widgets.window.DialogueOptions;
import org.jboss.gwt.circuit.Dispatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A miller column based selection of host/serve combinations
 *
 * @author Heiko Braun
 * @date 12/9/11
 */
public class HostServerTable {

    private static final int ESCAPE = 27;
    public final static double GOLDEN_RATIO = 1.618;
    private final Dispatcher circuit;

    private boolean isRightToLeft = false;

    private CellList<Host> hostList;
    private CellList<ServerInstance> serverList;

    private ListDataProvider<Host> hostProvider;
    private ListDataProvider<ServerInstance> serverProvider;

    private PopupPanel popup;

    private VerticalPanel header;
    private HTML currentDisplayedValue;
    int popupWidth = -1;
    private String description = null;
    private HTML ratio;
    private DefaultPager hostPager  ;
    private DefaultPager serverPager;

    private int clipAt = 20;

    private SingleSelectionModel<Host> hostSelectionModel;
    private SingleSelectionModel<ServerInstance> serverSelectionModel;


    public HostServerTable() {
        this.circuit = Console.MODULES.getCircuitDispatcher();
    }

    private static String clip(String value, int clipping)
    {
        String result = value;
        if(value!=null && value.length()>clipping)
            result = value.substring(0, clipping)+"...";
        return result;
    }

    public void setRightToLeft(boolean rightToLeft) {
        isRightToLeft = rightToLeft;
    }

    public void setPopupWidth(int popupWidth) {
        this.popupWidth = popupWidth;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Widget asWidget() {

        final String panelId = "popup_"+ HTMLPanel.createUniqueId();
        popup = new DefaultPopup(DefaultPopup.Arrow.TOPLEFT);
        popup.addStyleName("server-picker-popup");

        popup.getElement().setId(panelId);

        VerticalPanel layout = new VerticalPanel();
        layout.setStyleName("fill-layout-width");

        HorizontalPanel tools = new HorizontalPanel();
        tools.setStyleName("fill-layout-width");
        tools.setStyleName("server-picker-header");
        if(description!=null)
            tools.add(new HTML(description));

        layout.add(tools);

        ratio = new HTML("RATIO HERE");
        //layout.add(ratio);
        // --------------

        ProvidesKey<Host> hostKey = new ProvidesKey<Host>() {
            @Override
            public Object getKey(Host host) {
                return host.getName();
            }
        };

        ProvidesKey<ServerInstance> serverkey = new ProvidesKey<ServerInstance>() {
            @Override
            public Object getKey(ServerInstance server) {
                return server.getName()+"_"+server.getGroup();
            }
        };

        hostList = new DefaultCellList<Host>(new HostCell(), hostKey);
        hostSelectionModel = new SingleSelectionModel<Host>(hostKey);
        hostList.setSelectionModel(hostSelectionModel);

        hostList.addStyleName("host-list");
        hostList.setPageSize(6);
        hostList.addStyleName("fill-layout-width");
        hostList.addStyleName("clip-text") ;


        serverList = new DefaultCellList<ServerInstance>(new ServerCell(), serverkey);
        serverSelectionModel = new SingleSelectionModel<ServerInstance>(serverkey);
        serverList.setSelectionModel(serverSelectionModel);

        serverList.addStyleName("server-list");
        serverList.setPageSize(6);
        serverList.addStyleName("fill-layout-width");
        serverList.addStyleName("clip-text") ;

        hostProvider = new ListDataProvider<Host>(hostKey);
        serverProvider = new ListDataProvider<ServerInstance>(serverkey);

        hostProvider.addDataDisplay(hostList);
        serverProvider.addDataDisplay(serverList);

        hostList.getSelectionModel().addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent event) {

                serverProvider.setList(new ArrayList<ServerInstance>());

                Host selectedHost = getSelectedHost();

                if(selectedHost!=null)
                {
                    circuit.dispatch(new HostSelection(selectedHost.getName()));
                }
            }
        });

        serverList.getSelectionModel().addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent event) {

                ServerInstance server = getSelectedServer();
                Host selectedHost = getSelectedHost();

                if(selectedHost!=null &server!=null)
                {
                    circuit.dispatch(new SelectServerInstance(server));

                }

            }
        });


        HorizontalPanel millerHeader = new HorizontalPanel();
        Label host = new Label("Host");
        millerHeader.add(host);
        Label server = new Label("Server");

        millerHeader.add(server);

        host.getElement().getParentElement().setAttribute("width", "50%");
        server.getElement().getParentElement().setAttribute("width", "50%");


        layout.add(millerHeader);

        millerHeader.getElement().addClassName("server-picker-table-header");
        millerHeader.getElement().getParentElement().setAttribute("style", "vertical-align:bottom");

        HorizontalPanel millerPanel = new HorizontalPanel();
        millerPanel.setStyleName("fill-layout");
        millerPanel.addStyleName("server-picker-table");


        hostPager = new DefaultPager();
        hostPager.setDisplay(hostList);
        FlowPanel lhs = new FlowPanel();
        lhs.add(hostList);
        lhs.add(hostPager.asWidget());

        millerPanel.add(lhs);


        serverPager = new DefaultPager();
        serverPager.setDisplay(serverList);
        FlowPanel rhs = new FlowPanel();
        rhs.add(serverList);
        rhs.add(serverPager.asWidget());
        millerPanel.add(rhs);

        hostPager.setVisible(false);
        serverPager.setVisible(false);

        lhs.getElement().getParentElement().setAttribute("style", "border-right:1px solid #A7ABB4");
        lhs.getElement().getParentElement().setAttribute("width", "50%");
        lhs.getElement().getParentElement().setAttribute("valign", "top");
        rhs.getElement().getParentElement().setAttribute("valign", "top");
        rhs.getElement().getParentElement().setAttribute("width", "50%");

        ScrollPanel scroll = new ScrollPanel(millerPanel);
        layout.add(scroll);


        ClickHandler handler = new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                popup.hide();
            }
        };
        DialogueOptions options = new DialogueOptions(Console.CONSTANTS.help_close_help(), handler,
                Console.CONSTANTS.help_close_help(), handler);
        options.showCancel(false);
        layout.add(options);

        // --------------


        popup.setWidget(layout);


        // --------------

        currentDisplayedValue = new HTML("&nbsp;");
        currentDisplayedValue.getElement().setAttribute("style", "padding-bottom:10px; padding-left:5px");

        header = new VerticalPanel();

        header.getElement().setAttribute("width", "100%");
        header.getElement().setAttribute("cellspacing", "0");
        header.getElement().setAttribute("cellpadding", "0");
        header.getElement().setAttribute("border", "0");

        ClickHandler clickHandler = new ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                openPanel();
            }
        };

        DefaultButton btn = new DefaultButton("Change Server");
        btn.addClickHandler(clickHandler);
        btn.addStyleName("server-picker-btn");
        //btn.addStyleName("primary");

        HTML title = new HTML("Server Status");
        title.setStyleName("server-picker-section-header");
        header.add(title);
        header.add(currentDisplayedValue);
        header.add(btn);


        VerticalPanel p = new VerticalPanel();
        p.setStyleName("fill-layout-width");
        p.add(title);
        p.add(header);

        header.getElement().getParentElement().setClassName("server-picker-wrapper");
        return p;
    }

    public void clearDisplay() {
        currentDisplayedValue.setHTML(
                "Please select server ..."
        );
    }

    public void updateDisplay(String hostName, String serverName) {

        String host = clip(hostName, clipAt);
        String server = clip(serverName, clipAt);

        currentDisplayedValue.setHTML(
                "Host:&nbsp;<b>" + host + "</b><br/>" +
                        "Server:&nbsp;<b>" + server + "</b>"
        );

    }

    public Host getSelectedHost() {
        return ((SingleSelectionModel<Host>) hostList.getSelectionModel()).getSelectedObject();
    }

    private ServerInstance getSelectedServer() {
        return ((SingleSelectionModel<ServerInstance>) serverList.getSelectionModel()).getSelectedObject();
    }

    private void openPanel() {

        int winWidth = popupWidth!=-1 ? popupWidth : header.getOffsetWidth() * 2;
        int winHeight = (int) ( winWidth / GOLDEN_RATIO );

        popup.setWidth(winWidth +"px");
        popup.setHeight(winHeight + "px");

        // right to left
        if(isRightToLeft)
        {
            int popupLeft = header.getAbsoluteLeft() - (winWidth - header.getOffsetWidth());
            popup.setPopupPosition(
                    popupLeft-15,
                    header.getAbsoluteTop()+72
            );
        }
        else
        {
            int popupLeft = header.getAbsoluteLeft();
            popup.setPopupPosition(
                    popupLeft,
                    header.getAbsoluteTop()+72
            );
        }

        popup.show();

    }

    public void clearSelection() {
        currentDisplayedValue.setText("");
    }

    public void setServer(ServerInstance selectedServer, List<ServerInstance> server) {

        serverSelectionModel.clear();
        serverProvider.setList(server);
        serverProvider.flush();

        if(server.isEmpty())
        {
            currentDisplayedValue.setText("No Server");
        }

        serverSelectionModel.setSelected(selectedServer, true);

        serverPager.setVisible(server.size() >= 5);
    }

    public void setHosts(Host selectedHost, List<Host> hostModel) {
        ratio.setText("");

        hostSelectionModel.clear();
        hostProvider.setList(hostModel);
        hostProvider.flush();

        hostSelectionModel.setSelected(selectedHost, true);

        hostPager.setVisible(hostModel.size() >= 5);

    }


    interface Template extends SafeHtmlTemplates {
        @Template("<div class='server-selection-host'>{0}</div>")
        SafeHtml message(String title);
    }

    interface ServerTemplate extends SafeHtmlTemplates {
        @Template("<table class='server-selection-server' width='90%'><tr><td>{0}</td><td width='10%'><i class='{1}'></i></td></tr></table>")
        SafeHtml message(String title, String icon);
    }


    // -----

    private static final Template HOST_TEMPLATE = GWT.create(Template.class);
    private static final ServerTemplate SERVER_TEMPLATE = GWT.create(ServerTemplate.class);

    public class HostCell extends AbstractCell<Host> {

        @Override
        public void render(
                Context context,
                Host host,
                SafeHtmlBuilder safeHtmlBuilder)
        {
            safeHtmlBuilder.append(HOST_TEMPLATE.message(clip(host.getName(), clipAt)));
        }

    }

    public class ServerCell extends AbstractCell<ServerInstance> {

        @Override
        public void render(
                Context context,
                ServerInstance server,
                SafeHtmlBuilder safeHtmlBuilder)
        {
            String icon = server.isRunning() ? "icon-ok":"icon-ban-circle";
            String name = clip(server.getName(), clipAt);
            safeHtmlBuilder.append(SERVER_TEMPLATE.message(name, icon));
        }

    }
}

