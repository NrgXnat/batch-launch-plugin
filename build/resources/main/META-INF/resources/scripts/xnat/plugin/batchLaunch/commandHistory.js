var XNAT = getObject(XNAT || {});

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
}(function(){


/* =============== *
 * Command History *
 * =============== */

    console.log('commandHistory.js');

    var wrapperList,
    	rootUrl = XNAT.url.rootUrl,
        restUrl = XNAT.url.restUrl,
        csrfUrl = XNAT.url.csrfUrl;

    XNAT.plugin =
        getObject(XNAT.plugin || {});

    XNAT.plugin.batchLaunch =
        getObject(XNAT.plugin.batchLaunch || {});

    var historyTable, containerHistory;


    XNAT.plugin.batchLaunch.historyTable = historyTable =
        getObject(XNAT.plugin.batchLaunch.historyTable || {});

    XNAT.plugin.batchLaunch.containerHistory = containerHistory =
        getObject(XNAT.plugin.batchLaunch.containerHistory || {});

    XNAT.plugin.batchLaunch.wrapperList = wrapperList = {};


    function getCommandHistoryUrl(appended){
        appended = (appended) ? '?'+appended : '';
        return restUrl('/xapi/containers' + appended);
    }

    function viewHistoryDialog(e, onclose){
        e.preventDefault();
        var historyId = $(this).data('id') || $(this).closest('tr').prop('title');
        XNAT.plugin.batchLaunch.historyTable.viewHistory(historyId);
    }

   function errorHandler(e, title, closeAll){
        console.log(e);
        title = (title) ? 'Error Found: '+ title : 'Error';
        closeAll = (closeAll === undefined) ? true : closeAll;
        var errormsg = (e.statusText) ? '<p><strong>Error ' + e.status + ': '+ e.statusText+'</strong></p><p>' + e.responseText + '</p>' : e;
        XNAT.dialog.open({
            width: 450,
            title: title,
            content: errormsg,
            buttons: [
                {
                    label: 'OK',
                    isDefault: true,
                    close: true,
                    action: function(){
                        if (closeAll) {
                            xmodal.closeAll();

                        }
                    }
                }
            ]
        });
    }


    function sortHistoryData(myProject,callback){
        callback = isFunction(callback) ? callback : function(){};

        var URL = getCommandHistoryUrl();
        return XNAT.xhr.getJSON(URL)
            .success(function(data){
                if (data.length){
                    // sort data by ID
                    data = data.sort(function(a,b){ return (a.id > b.id) ? 1 : -1 });

                    // add a project field before returning. For setup containers, this requires some additional work.
                    var setupContainers = data.filter(function(a) { return (a.subtype) ? a.subtype.toLowerCase() === 'setup' : false });
                    setupContainers.forEach(function(entry){
                        var projectId = getProjectIdFromMounts(entry);
						data[entry.id - 1].project = projectId;

						if (entry['parent-database-id']) {
							data[entry['parent-database-id']-1].project = projectId;
							data[entry['parent-database-id']-1]['setup-container-id'] = entry.id;
						}
                    });

                    // copy the history listing into an object for individual reference
                    data.forEach(function(historyEntry){
                        containerHistory[historyEntry.id] = historyEntry;
                    });
                }
                callback.apply(this, arguments);
            })
    }

  function getProjectIdFromMounts(entry){
 	        var mounts = entry.mounts;
 		    // assume that the first mount of a container is an input from a project. Parse the URI for that mount and return the project ID.
 		    //This does not work all the time - so traverse all the array elements to get the host-path
 			var	mLen = mounts.length;
 		    var i,found = 0;
 		    var project = "Unknown";
 		    if (mLen) {
 				for (i = 0; i < mLen; i++) {
 				   var inputMount = mounts[i]['xnat-host-path'];
 				   if (inputMount === undefined) continue;
 				   var ind = inputMount.indexOf("/data/archive/");
 				   if (ind == -1) {
 						continue;
 					}else {
 						 inputMount = inputMount.substr(ind+14);
 					}
 					var inputMountEls = inputMount.split('/');
 					project = inputMountEls[0];
 					found = 1;
 					break;
 			     }
 			     if (found){
 			       return project;
 			     }else {
 			        return false;
 			     }
 	        } else {
 		        return false;
 	        }
    }

    function spawnHistoryTable(sortedHistoryObj){

        var $dataRows = [];

        var styles = {
            image: (150-24)+'px',
            command: (200-24) + 'px',
            user: (120-24) + 'px',
            date: (100-24) + 'px',
        };
        // var altStyles = {};
        // forOwn(styles, function(name, val){
        //     altStyles[name] = (val * 0.8)
        // });
        return {
            kind: 'table.dataTable',
            name: 'userProfiles',
            id: 'user-profiles',
            // load: URL,
            data: sortedHistoryObj,
            before: {
                filterCss: {
                    tag: 'style|type=text/css',
                    content: '\n' +
                    '#command-history-container td.history-id { width: ' + styles.id + '; } \n' +
                    '#command-history-container td.user .truncate { width: ' + styles.user + '; } \n' +
                    '#command-history-container td.date { width: ' + styles.date + '; } \n' +
                    '#command-history-container tr.filter-timestamp { display: none } \n'
                }
            },
            table: {
                classes: 'highlight hidden',
                on: [
                    ['click', 'a.view-history', viewHistoryDialog]
                ]
            },
            trs: function(tr, data){
                tr.id = data.id;
                addDataAttrs(tr, { filter: '0' });
            },
            sortable: 'image, command, user, DATE',
            filter: 'image, command, user, DATE',
            items: {
                // by convention, name 'custom' columns with ALL CAPS
                // 'custom' columns do not correspond directly with
                // a data item
                DATE: {
                    label: 'Date',
                    th: { className: 'container-launch center' },
                    td: { className: 'container-launch center mono'},
                    filter: function(table){
                        var MIN = 60*1000;
                        var HOUR = MIN*60;
                        var X8HRS = HOUR*8;
                        var X24HRS = HOUR*24;
                        var X7DAYS = X24HRS*7;
                        var X30DAYS = X24HRS*30;
                        return spawn('div.center', [XNAT.ui.select.menu({
                            value: 0,
                            options: {
                                all: {
                                    label: 'All',
                                    value: 0,
                                    selected: true
                                },
                                lastHour: {
                                    label: 'Last Hour',
                                    value: HOUR
                                },
                                last8hours: {
                                    label: 'Last 8 Hrs',
                                    value: X8HRS
                                },
                                last24hours: {
                                    label: 'Last 24 Hrs',
                                    value: X24HRS
                                },
                                lastWeek: {
                                    label: 'Last Week',
                                    value: X7DAYS
                                },
                                last30days: {
                                    label: 'Last 30 days',
                                    value: X30DAYS
                                }
                            },
                            element: {
                                id: 'filter-select-container-timestamp',
                                on: {
                                    change: function(){
                                        var FILTERCLASS = 'filter-timestamp';
                                        var selectedValue = parseInt(this.value, 10);
                                        var currentTime = Date.now();
                                        $dataRows = $dataRows.length ? $dataRows : $$(table).find('tbody').find('tr');

                                        if (selectedValue === 0) {
                                            $dataRows.removeClass(FILTERCLASS);
                                        }
                                        else {
                                            $dataRows.addClass(FILTERCLASS).filter(function(){
                                                var timestamp = this.querySelector('input.container-timestamp');
                                                var containerLaunch = +(timestamp.value);
                                                return selectedValue === containerLaunch-1 || selectedValue > (currentTime - containerLaunch);
                                            }).removeClass(FILTERCLASS);
                                        }
                                    }
                                }
                            }
                        }).element])
                    },
                    apply: function(){
                        var timestamp = 0, dateString;
                        if (this.history.length > 0){
                            this.history.forEach(function(h){
                                if(h['status'] === 'Created') {
                                    timestamp = h['time-recorded'];
                                    dateString = new Date(timestamp);
                                    dateString = dateString.toISOString().replace('T',' ').replace('Z',' ').split('.')[0];
                                }
                            });
                        } else {
                            dateString = 'N/A';
                        }
                        return spawn('!',[
                            spawn('span', dateString ),
                            spawn('input.hidden.container-timestamp.filtering|type=hidden', { value: timestamp } )
                        ])
                    }
                },
                image: {
                    label: 'Image',
                    filter: true, // add filter: true to individual items to add a filter,
                    apply: function(){
                        return this['docker-image'];
                    }
                },
                command: {
                    label: 'Command',
                    filter: true,
                    apply: function(){
                        var label = (wrapperList[ this['wrapper-id'] ]) ?
                            (wrapperList[ this['wrapper-id'] ].description) ?
                                wrapperList[ this['wrapper-id'] ].description :
                                wrapperList[ this['wrapper-id'] ].name
                            : this['command-line'];

                        return spawn('a.view-history', {
                            href: '#!',
                            title: 'View command history and logs',
                            data: {'id': this.id },
                            html: label
                        });
                    }
                },
                user: {
                    label: 'User',
                    filter: true,
                    apply: function(){
                        return this['user-id']
                    }
                }
            }
        }
    }


    historyTable.viewLog = viewLog = function(containerId,logFile){
        XNAT.xhr.get ({
            url: rootUrl('/xapi/containers/'+containerId+'/logs/'+logFile),
            success: function(data){
                // split the output into lines
                data = data.split('\n');

                XNAT.dialog.open({
                    title: 'View '+logFile,
                    width: 500,
                    content: null,
                    beforeShow: function(obj){
                        data.forEach(function(newLine){
                            obj.$modal.find('.xnat-dialog-content').append(spawn('code',{ 'style': { 'display': 'block' }}, newLine));
                        });
                    },
                    buttons: [
                        {
                            label: 'OK',
                            isDefault: true,
                            close: true
                        }
                    ]
                })
            },
            fail: function(e){
                errorHandler(e, 'Cannot retrieve '+logFile);
            }
        })
    };

    historyTable.viewHistory = function(id){
        if (containerHistory[id]) {
            var historyEntry = XNAT.plugin.batchLaunch.containerHistory[id];
            var historyDialogButtons = [
                {
                    label: 'OK',
                    isDefault: true,
                    close: true
                }
            ];

            // build nice-looking history entry table
            var pheTable = XNAT.table({
                className: 'xnat-table compact',
                style: {
                    width: '100%',
                    marginTop: '15px',
                    marginBottom: '15px'
                }
            });

            // add table header row
            pheTable.tr()
                .th({ addClass: 'left', html: '<b>Key</b>' })
                .th({ addClass: 'left', html: '<b>Value</b>' });

            for (var key in historyEntry){
                var val = historyEntry[key], formattedVal = '';
                if (Array.isArray(val)) {
                    var items = [];
                    val.forEach(function(item){
                        if (typeof item === 'object') item = JSON.stringify(item);
                        items.push(spawn('li',[ spawn('code',item) ]));
                    });
                    formattedVal = spawn('ul',{ style: { 'list-style-type': 'none', 'padding-left': '0' }}, items);
                } else if (typeof val === 'object' ) {
                    formattedVal = spawn('code', JSON.stringify(val));
                } else if (!val) {
                    formattedVal = spawn('code','false');
                } else {
                    formattedVal = spawn('code',val);
                }

                pheTable.tr()
                    .td('<b>'+key+'</b>')
                    .td([ spawn('div',{ style: { 'word-break': 'break-all','max-width':'600px' }}, formattedVal) ]);

                // check logs and populate buttons at bottom of modal
                if (key === 'log-paths') {
					historyDialogButtons.push({
						label: 'View StdOut.log',
						close: false,
						action: function(){
							historyTable.viewLog(historyEntry['container-id'],'stdout')
						}
					});

					historyDialogButtons.push({
						label: 'View StdErr.log',
						close: false,
						action: function(){
							historyTable.viewLog(historyEntry['container-id'],'stderr')
						}
					})
                }
                if (key === 'setup-container-id') {
                    historyDialogButtons.push({
                        label: 'View Setup Container',
                        close: true,
                        action: function(){
                            historyTable.viewHistory(historyEntry[key]);
                        }
                    })
                }
                if (key === 'parent-database-id' && historyEntry[key]) {
                    var parentId = historyEntry[key];
                    historyDialogButtons.push({
                        label: 'View Parent Container',
                        close: true,
                        action: function(){
                            historyTable.viewHistory(parentId);
                        }
                    })
                }

            }

            // display history
            XNAT.ui.dialog.open({
                title: historyEntry['wrapper-name'],
                width: 800,
                scroll: true,
                content: pheTable.table,
                buttons: historyDialogButtons
            });
        } else {
            console.log(id);
            XNAT.ui.dialog.open({
                content: 'Sorry, could not display this history item.',
                buttons: [
                    {
                        label: 'OK',
                        isDefault: true,
                        close: true
                    }
                ]
            });
        }
    };

    historyTable.init = historyTable.refresh = function(myProject, container){
        var $manager = $$(container || '#command-history-container'),
            _historyTable;

        sortHistoryData(myProject).done(function(data){
            if (data.length) {
				if (myProject) {
					data = data.filter(function(a) {
										    if (a.project) {
												   return a.project === myProject;
											}else {
											   var  proj = getProjectIdFromMounts(a);
											   if (proj) {
												  return proj === myProject;
											   }else {
												   return false;
											   }
											}
										});
				}

                // sort list of container launches by execution time, descending
                data = data.sort(function(a,b){
                    return (a.history[0]['time-recorded'] < b.history[0]['time-recorded']) ? 1 : -1
                });

                setTimeout(function(){
                    $manager.html('loading...');
                }, 1);
                setTimeout(function(){
                    _historyTable = XNAT.spawner.spawn({
                        historyTable: spawnHistoryTable(data)
                    });
                    _historyTable.done(function(){
                        $manager.empty().append(
                            spawn('h3', { style: { 'margin-bottom': '1em' }}, data.length + ' Containers Launched On Project <a href="' + rootUrl('/data/projects/'+ myProject + '?format=html">' + myProject + '</a>')),


                        );
                        this.render($manager, 20);
                    });
                }, 10);
                // return _usersTable;
            }
        });

    };

    // Don't call this until the command list has been populated.
    // historyTable.init();
}));

