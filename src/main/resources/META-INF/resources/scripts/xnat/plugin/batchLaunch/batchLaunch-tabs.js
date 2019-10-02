/*
 * Copyright 2019 Radiologics, Inc
 */

console.log('batchLaunch-tabs.js');

var XNAT = getObject(XNAT || {});
XNAT.plugin = getObject(XNAT.plugin || {});
XNAT.plugin.containerService = getObject(XNAT.plugin.containerService || {});
XNAT.plugin.batchLaunch = getObject(XNAT.plugin.batchLaunch || {});


(function(factory){
    if (typeof define === 'function' && define.amd) {
        define(factory);
    }
    else if (typeof exports === 'object') {
        module.exports = factory();
    }
    else {
        return factory();
    }
}(function() {
    var processingTabs = new YAHOO.widget.TabView('processing_tabs');
    var containerTableId = "command-history-container";
    var containerHistoryLoaded = false;
    var allHistoryLoaded = false;

    if (!XNAT.plugin.batchLaunch.projectId) {
        XNAT.plugin.batchLaunch.projectId = XNAT.data.context.projectID || XNAT.data.context.project;
    }

    // Add tabs, initialize on activation only
    // Launch
    var launchTab = new YAHOO.widget.Tab({
        active: true,
        label: 'Launch processes',
        content : '<div class="tab-container"><div class="data-table-container"  id="selectable-table-bulk"></div></div>'
    });
    processingTabs.addTab(launchTab);

    // Container history
    // var containersTab = new YAHOO.widget.Tab({
    //     label: 'Container history',
    //     content: '<div class="tab-container"><div class="data-table-container" id="' + containerTableId + '" style="overflow-x:auto;overflow-y:auto;"></div></div>'
    // });
    // containersTab.addListener('activeChange', function (e) {
    //     if (e.newValue && !containerHistoryLoaded) {
    //         XNAT.plugin.containerService.historyTable.init(XNAT.plugin.batchLaunch.projectId);
    //         XNAT.plugin.batchLaunch.setTableWidth('div-xnat-table', containerTableId);
    //         XNAT.plugin.batchLaunch.setTableHeight(containerTableId);
    //         containerHistoryLoaded = true;
    //     }
    // });
    // processingTabs.addTab(containersTab);

    // All project history, only show if we have a single project
    if (XNAT.plugin.batchLaunch.projectId) {
        var historyTab = new YAHOO.widget.Tab({
            label: 'All processing history',
            content: '<div class="tab-container"><div class="data-table-container" id="workflows-data-table-container" ></div></div>'
        });
        historyTab.addListener('activeChange', function (e) {
            if (e.newValue && !allHistoryLoaded) {
                XNAT.plugin.batchLaunch.workflowTable.load();
                allHistoryLoaded = true;
            }
        });
        processingTabs.addTab(historyTab);
    }

    XNAT.plugin.batchLaunch.launchTable.init();
}));
