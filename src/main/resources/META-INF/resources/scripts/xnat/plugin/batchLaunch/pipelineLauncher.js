/*
 * web: pipelineUiLauncher.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2019-2020, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Flexible script to be used in the Pipeline UI to launch
 */

console.log('pipelineUiLauncher.js');

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

    var launcher,
        undefined,
        launcherMenu = $('#pipeline-launch-menu'),
        rootUrl = XNAT.url.rootUrl,
        csrfUrl = XNAT.url.csrfUrl,
        projectId = XNAT.data.context.projectID,
        xsiType = XNAT.data.context.xsiType,
        containerMenuItems;

    XNAT.plugin =
        getObject(XNAT.plugin || {});

    XNAT.plugin.pipelineLaunchService =
        getObject(XNAT.plugin.pipelineLaunchService || {});

    XNAT.plugin.pipelineLaunchService.launcher = launcher =
        getObject(XNAT.plugin.pipelineLaunchService.launcher || {});

    function getPipelineDetailsUrl(projectId,pipelineName){
		var postfix = '';
		if (projectId) {
		  postfix += '&project=' + projectId;
		}
        return csrfUrl('/xapi/pipelines/parameters?pipelinename='+pipelineName + postfix);
    }

	function getBatchPipelineLaunchUrl(pipelineName,projectId) {
		var postfix = '';
		if (projectId) {
		  postfix += 'project=' + projectId;
		}
		return csrfUrl('/xapi/pipelines/launch/'+pipelineName + '?'+postfix);
	}


   function errorHandler(e){
        var details = e.responseText ? spawn('p',[e.responseText]) : '';
        console.log(e);
        xmodal.alert({
            title: 'Error',
            content: '<p><strong>Error ' + e.status + ': '+ e.statusText+'</strong></p>' + details.html,
            okAction: function () {
                xmodal.closeAll();
            }
        });
    }
   var helptext = function(description) {
        return (description) ? '' : spawn('div.description',description);
    };
    var vertSpace = function(condensed) {
        return (condensed) ? '' : spawn('br.clear');
    };

   launcher.noIllegalChars = function(input,exception){
        // examine the to-be-submitted value of an input against a list of disallowed characters and return false if any are found.
        // if an input needs to allow one of these strings, an exception can be passed to this function
        exception = exception || null;
        var illegalCharset = [';', '\\|\\|', '&&', '\\(', '`' ],
            value = input.value,
            pass = true;

        illegalCharset.forEach(function(test){
            if (value.match(test) && test !== exception) {
                pass = false;
            }
        });

        return pass;
    };


    var defaultConfigInput = function(input){
		  var name = input.name,
		  value = input.value,
		  description = input.description;

           var required = false,
            classes = ['panel-input'],
            dataProps = {};
        value = (value === undefined || value === null || value == 'null') ? '' : value;
        var label = label || name;
        description = description || '';


        return XNAT.ui.panel.input.text({
            name: name,
            value: value,
            description: description,
            label: label,
            data: dataProps,
            className: classes.join(' ')
        }).element;
    };


    var configCheckbox = function(input){
		  var name = input.name,
		  value = input.value,
		  description = input.description;
		  var valueArr = value.split(',');
            var outerLabel = name,
            description = description || '',
            required = false,
            condensed = false,
            classes = ['panel-element panel-input'],
            dataProps = { name: name },
            disabled = false,
        attr = {};


        return spawn('div', { className: classes.join(' '), data: dataProps }, [
            spawn('label.element-label', outerLabel),
            spawn('div.element-wrapper', [
                spawn('label', spawn('input', { type: 'checkbox', name: name, value: v, attr: attr }),v),
                helptext(description)
            ]),
            vertSpace(condensed)
        ]);
    };

    var staticConfigList = function(input) {
        var value = input.value,
            name = input.name,
            label = input.label || input.name,
            valueLabel = input.valueLabel;
        var listArray = Array.isArray(valueLabel) ? valueLabel : valueLabel.split(",");
        var elemWrapperContent;
        if (listArray.length > 6) {
            elemWrapperContent = spawn('textarea',{ 'readonly':true, style: { height: '80px' }},listArray.join('\n'));
        } else {
            listArray.forEach(function(item,i){
                listArray[i] = '<li>'+item+'</li>'
            });
            elemWrapperContent = spawn('ul',{ style: {
                    'list-style-type': 'none',
                    margin: 0,
                    padding: 0
                }},listArray.join(''));
        }
        return spawn(
            'div.panel-element', { data: { name: name } }, [
                spawn('label.element-label', label),
                spawn('div.element-wrapper', [elemWrapperContent]),
                spawn('input',{
                    type: 'hidden',
                    name: name,
                    value: JSON.stringify(value)
                }),
                spawn('br.clear')
            ]
        )
    };


    var hiddenConfigInput = function(input) {
        var name = input.name || input.label,
            value = input.value,
            dataProps = {},
            classes = [],
            attr = (input.disabled) ? { 'disabled':'disabled' } : {};

        if (input.childInputs) {
            classes.push('has-children');
            dataProps['children'] = input.childInputs.join(',');
        }

        return XNAT.ui.input.hidden({
            name: name,
            value: value,
            data: dataProps,
            attr: attr
        }).element;
    };

    launcher.errorMessages = [];

       launcher.formInputs = function(input) {
	        var formPanelElements = [];
	        switch (input.type) {
				case 'staticList':
			                formPanelElements.push(staticConfigList(input));
            			    break;
            	case 'hidden':
                			formPanelElements.push(hiddenConfigInput(input));
                			break;
            	 default:
            	 				try {
										var valueArr = input.value.split(',');
										console.log(input.name + " ValueArr: " + valueArr.length);
									//	if (valueArr.length>1) {
									//      formPanelElements.push(configCheckbox(input));
									//	}else {
										  formPanelElements.push(defaultConfigInput(input));
									//	}
								}catch(err) {console.log("Could not render " + input.name + " " + err);}

			}
	        return formPanelElements;
	    };


  launcher.bulkLaunchPipelineDialog = function(pipelineName,targets, targetLabels, project){
        // 'targets' should be formatted as a one-dimensional array of XNAT URIs (i.e. scan URIs, session URIs)
        // the 'root element' should match one of the inputs in the command config object, and overwrite it with the
        // values provided in the 'targets' array

        if (projectId.length && !project) project = projectId;

        if (!targets || targets.length === 0) return false;
        if (!targetLabels || targetLabels.length !== targets.length) {
            targetLabels = $.map(targets, function(t) {return t.replace(/.*\//,'');});
        }

        xmodal.loading.open({ title: 'Configuring Container Launcher' });

        var launchUrl = getPipelineDetailsUrl(projectId,pipelineName);

        XNAT.xhr.getJSON({
            url: launchUrl,
            //data: {'sampleTarget': sampleTarget, 'rootElement': rootElement},
            fail: function(e){
                xmodal.loading.close();
                errorHandler({
                    statusText: e.statusText,
                    responseText: 'Could not build bulk UI for pipeline : "' +
                        pipelineName + '" project:"' + projectId + '".'
                });
            },
            success: function(configData){
                launchPipeline(configData, pipelineName, targets, targetLabels, project);
            }
        });
    };

        /*
	     ** Launcher Options
	     */

	    function launchPipeline(configData,pipelineName,targets,targetLabels,project){
	        launcher.inputList = configData['inputParameters'];

	        var runLabel = 'Run Job';
	        var projectContext = XNAT.data.context.project;
	        var launchUrl = '';
	        var bulkLaunch = false;

	        var formContent = [spawn('p','Please specify settings')];

	        if (targets) {
	            // Bulk launch
	            bulkLaunch = true;
	            formContent.push(spawn('div.target-list.bulk-inputs'));
	            runLabel += "(s)";
	            batchPipelineLaunchUrl = getBatchPipelineLaunchUrl(pipelineName,project);
	        }

	        formContent.push(
	            spawn('div.standard-settings')

	        );

	        var launcherContent = spawn('div.panel',[spawn('form',formContent)]);
	            XNAT.ui.dialog.open({
	                title: 'Set Job Launch Values',
	                content: launcherContent,
	                width: 550,
	                maxBtn: true,
	                scroll: true,
	                beforeShow: function(obj){
	                    launcher.errorMessages = [];
	                    xmodal.loading.close();
	                    xmodal.loading.open({title: 'Rendering Job Launcher'});
	                    var $panel = obj.$modal.find('.panel'),
	                    $targetListContainer = $panel.find('.target-list');

	                    if (bulkLaunch && $targetListContainer.length > 0) {
	                        // display root elements first
	                        $targetListContainer.append(spawn('p',[ spawn('strong', targets.length + ' item(s) selected to run in bulk.' )]));
	                         var targetList = launcher.formInputs({
							                            name: 'Experiments',
							                            type: 'staticList',
							                            valueLabel: targetLabels,
							                            value: targets,
							                            description : ''
							                        });
                        	$targetListContainer.append(targetList);
	                    }
		                    launcher.populateForm($panel,launcher.inputList);

	                },
	                afterShow: function(obj){
	                    xmodal.loading.close();
	                    if (isArray(launcher.errorMessages) && launcher.errorMessages.length) {
	                        var $panel = obj.$modal.find('.panel');
	                        launcher.errorMessages.forEach(function(msg){
	                            $panel.prepend(spawn('div.warning',{style: { 'margin-bottom': '1em' }},msg));
	                        });
	                    }
	                },
	                buttons: [
	                    {
	                        label: runLabel,
	                        isDefault: true,
	                        close: false,
	                        action: function(obj){
	                            var $form = obj.$modal.find('.panel form');

	                            // check all inputs for invalid characters
	                            var $inputs = $form.find('input'),
	                                runContainer = true;
	                            //$inputs.each(function(){
	                            //    var input = $(this)[0];
	                            //    if (!launcher.noIllegalChars(input)) {
	                            //        runContainer = false;
	                            //        $(this).addClass('invalid');
	                            //    }
	                            //});

	                            if (runContainer) {
	                                if (!bulkLaunch) xmodal.loading.open({ title: 'Launching Container...' });

	                                // gather form input values
	                                //Need to resolve the schemaLink values
	                                var dataToPost = form2js($form.get(0), ':', false);
	                                console.log("Data To Post: " + JSON.stringify(dataToPost));
	                                // API method can only receive Map<String, String> so need to stringify more complex params
	                                // with plan to deserialize them deeper in the code
	                                $.each(dataToPost, function(key, value) {
	                                    if ($.type(value) !== "string") {
	                                        dataToPost[key] = JSON.stringify(value);
	                                    }
	                                });

	                                XNAT.xhr.postJSON({
	                                    beforeSend: function() {
	                                        if (bulkLaunch) {
	                                            XNAT.ui.dialog.closeAll();
	                                            XNAT.ui.dialog.alert("Containers are being launched in the background. " +
	                                                "You may continue to work, monitoring workflows to see updated progress.");
	                                        }
	                                        return true;
	                                    },
	                                    url: batchPipelineLaunchUrl,
	                                    data: JSON.stringify(dataToPost),
	                                    success: function(data){
	                                        if (bulkLaunch) {
	                                            // bulk launch success returns two arrays -- containers that successfully launched, and containers that failed to launch
	                                            var messageContent = [],
	                                                totalLaunchAttempts = data.successes.concat(data.failures).length;
	                                            if (data.failures.length > 0) {
	                                                messageContent.push( spawn('div.message',data.successes.length + ' of '+totalLaunchAttempts+' jobs successfully queued for launch.') );
	                                            } else if(data.successes.length > 0) {
	                                                messageContent.push( spawn('div.success','All jobs successfully queued for launch.') );
	                                            } else {
	                                                errorHandler({
	                                                    statusText: 'Something went wrong. No jobs were queued for launch.'
	                                                });
	                                            }


	                                            XNAT.ui.dialog.open({
	                                                title: 'Job launch report',
	                                                content: spawn('div', messageContent ),
	                                                buttons: [
	                                                    {
	                                                        label: 'OK',
	                                                        isDefault: true,
	                                                        close: true,
	                                                        action: XNAT.ui.dialog.closeAll()
	                                                    }
	                                                ]
	                                            });
	                                        } else {
	                                            xmodal.loading.close();
	                                            var messageContent;

	                                            XNAT.ui.dialog.open({
	                                                title: 'Job Launch <span style="text-transform: capitalize">'+data.status+'</span>',
	                                                content: messageContent,
	                                                buttons: [
	                                                    {
	                                                        label: 'OK',
	                                                        isDefault: true,
	                                                        close: true,
	                                                        action: XNAT.ui.dialog.closeAll()
	                                                    }
	                                                ]
	                                            });
	                                        }
	                                    },
	                                    fail: function (e) {
	                                        if (!bulkLaunch) xmodal.loading.close();

	                                        if (e.responseJSON && e.responseJSON.message) {
	                                            var data = e.responseJSON;
	                                            var messageContent = spawn('div',[
	                                                spawn('p',{ style: { 'font-weight': 'bold' }}, 'Error Message:'),
	                                                spawn('pre.json', data.message),
	                                                spawn('p',{ style: { 'font-weight': 'bold' }}, 'Parameters Submitted To XNAT:'),
	                                                spawn('div', prettifyJSON(data.params))
	                                            ]);

	                                            XNAT.ui.dialog.open({
	                                                title: 'Job Launch <span style="text-transform: capitalize">'+data.status+'</span>',
	                                                content: messageContent,
	                                                buttons: [
	                                                    {
	                                                        label: 'OK',
	                                                        isDefault: true,
	                                                        close: true,
	                                                        action: XNAT.ui.dialog.closeAll()
	                                                    }
	                                                ]
	                                            });
	                                        } else {
	                                            errorHandler(e);
	                                        }
	                                    }
	                                });

	                            } else {
	                                // don't run container if invalid characters are found
	                                XNAT.dialog.open({
	                                    title: 'Cannot Launch Container',
	                                    content: 'Illegal characters were found in your inputs. Please correct this and try again.',
	                                    width: 400,
	                                    buttons: [
	                                        {
	                                            label: 'OK',
	                                            isDefault: true,
	                                            close: true
	                                        }
	                                    ]
	                                });
	                                return false;
	                            }

	                        }
	                    },
	                    {
	                        label: 'Cancel',
	                        isDefault: false,
	                        close: true
	                    }
	                ]
	            });

	    }

    launcher.populateForm = function($form,inputList){
        //targets is an array of URI like /archive/experiments/XNAT_ID
        //targetLabels is an array of corresponding session labels
        // receive $form as a jquery form object
        // the inputList looks like a collection of
        //
        //{"values": {
        //   "schemaLink": "xnat:petSessionData/subject_ID"
      //  },
      //  "name": "subject",
      //  "description": "Subject ID"
      //   },
    //OR {
     // "values": {
     //   "csv": "MRLABEL"
     // },
     // "name": "mrLabel",
     // "description": "MR session label for manual and FS ROI processing"
    // }

        function renderInput( input, $form){
			var $standardInputContainer = $form.find('.standard-settings');

			var $addTo = $standardInputContainer;
			$addTo.append(launcher.formInputs(input));

        }
			inputList.forEach(function(thisInput){
				var inputName = thisInput['name'];
				var inputCsvValue = thisInput['values']['csv'];
				var inputSchemaLink = thisInput['values']['schemaLink'];
				var inputDescription = thisInput['description'];

				var input = {};
				input.name = inputName;
				input.valueLabel = '';
				input.description = inputDescription;
				if (typeof inputCsvValue !== 'undefined' && inputCsvValue != null) {
					input.type = 'inputParameterCsv';
					input.value = inputCsvValue;
				    renderInput(input, $form);
				}else if (typeof inputSchemaLink !== 'undefined' && inputSchemaLink != null) {
					input.type = 'hidden';
					input.name='xnatschemaLink-' + input.name;
					input.value = inputSchemaLink;
				    renderInput(input, $form);
				}
			});
    };



}));


