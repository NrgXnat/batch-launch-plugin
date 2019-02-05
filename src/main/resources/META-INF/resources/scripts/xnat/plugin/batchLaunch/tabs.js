var historyTableId = "active-processes";

function setTableWidth(div_id, div_title_id) {
    var tableC = YUIDOM.get(div_id);// have to use YUI here because jquery fails
                                    // to find it. I think because it contains a
                                    // period. But, the YUI element can be
                                    // passed into jquery.
    if (tableC != null) {
        var tableWidth = $(YUIDOM.getFirstChild(tableC)).width();// need the
        // width of
        // the table
        // within
        // the
        // container
        // div.
        var tabsWidth = $('#processing_tabs').width();
        console.log("Table Width: " + tableWidth + " Tab Width:" + tabsWidth);
        if ((tableWidth + 18) < tabsWidth) {// if table + scrollbar doesn't take
            // up the whole tab
            setItemWidth(div_id, (tableWidth + 18));// set table overflow
            // container to barely
            // contain table, so the
            // scrollbar isn't way off
            // to the right.
            setItemWidth(div_title_id, (tableWidth + 18));
        }
    }
}

function setTableHeight(div_id) {
    var container = document.getElementById(div_id);
    container = $(container);
    if ($(container) != null) {
        var windowHeight = $(window).innerHeight();
        var tableHeight = $('table.xnat-table').height();
        var tablePosition = $(container).offset();

        /*
         * max height is total screen height minus space for table header &
         * chrome
         */
        var maxTableHeight = (tableHeight < windowHeight) ? tableHeight + 60 : windowHeight - 60;
        var minTableHeight = 300;

        /*
         * available height is visible screen height below the starting Y point
         * of the table, plus room for table header & chrome
         */
        var availableTableHeight = windowHeight - 30;
        availableTableHeight = (availableTableHeight > maxTableHeight) ? maxTableHeight : availableTableHeight;
        availableTableHeight = (availableTableHeight < minTableHeight) ? minTableHeight : availableTableHeight;

        /* set dimensions of table containers */
        $(container).css('height', availableTableHeight);
    }
}

var processingTabs = new YAHOO.widget.TabView('processing_tabs');

processingTabs.addTab(new YAHOO.widget.Tab({
    active: true,
    label: 'Launch Processes',
    content : '<div class="tab-container"><div class="data-table-container"  id="selectable-table-bulk"></div></div>'
}));

var historyTab = new YAHOO.widget.Tab({
    label: 'Processing History',
    content: '<div class="active-processes-class" id="active-processes" style="overflow-x:scroll;overflow-y:scroll;padding-right:0;"></div>'
});

historyTab.addListener('activeChange', function(e) {
    if (e.newValue) {
        XNAT.plugin.batchLaunch.historyTable.init(projectId, "#" + historyTableId);
        setTableWidth('div-xnat-table', historyTableId);
        setTableHeight(historyTableId);
    }
});

processingTabs.addTab(historyTab);
