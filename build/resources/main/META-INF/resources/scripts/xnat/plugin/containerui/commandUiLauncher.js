/*
 * web: commandUiLauncher.js
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

/*!
 * Flexible script to be used in the UI to launch
 */

console.log('commandUiLauncher-mr.js');

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
        launcherMenu = $('#container-launch-menu'),
        rootUrl = XNAT.url.rootUrl,
        csrfUrl = XNAT.url.csrfUrl,
        projectId = XNAT.data.context.projectID,
        xsiType = XNAT.data.context.xsiType;
       // containerMenuItems;

    XNAT.plugin =
        getObject(XNAT.plugin || {});

    XNAT.plugin.containerService =
        getObject(XNAT.plugin.containerService || {});

    XNAT.plugin.containerService.launcher = launcher =
        getObject(XNAT.plugin.containerService.launcher || {});


    function errorHandler(e){
        console.log(e);
        xmodal.alert({
            title: 'Error',
            content: '<p><strong>Error ' + e.status + ': '+ e.statusText+'</strong></p>',
            okAction: function () {
                xmodal.closeAll();
            }
        });
    }
    function getEnabledCommandsUrl(appended){
        appended = isDefined(appended) ? appended : '';
        return rootUrl('/xapi/commands' + appended);
    }

    function getCommandUrl(commandId,wrapperName,appended){
        if (!commandId || !wrapperId) return false;
        appended = (appended) ? '/' + appended : '';
        return rootUrl('/xapi/commands/'+commandId+'/wrappers/'+wrapperName+appended);
    }

    function getProjectCommandConfigUrl(commandId,wrapperName){
        if (!projectId || !commandId || !wrapperName) return false;
        return rootUrl('/xapi/projects/'+projectId+'/commands/'+commandId+'/wrapper/'+wrapperName+'/config');
    }
    function getLauncherUI(wrapperId,rootElementName,rootElementValue){
        return rootUrl('/xapi/wrappers/'+wrapperId+'/launch?'+rootElementName+'='+rootElementValue);
    }
    function getProjectLauncherUI(wrapperId,rootElementName,rootElementValue){
        return rootUrl('/xapi/projects/'+projectId+'/wrappers/'+wrapperId+'/launch?'+rootElementName+'='+rootElementValue);
    }
    function containerLaunchUrl(wrapperId){
        return csrfUrl('/xapi/wrappers/'+wrapperId+'/launch');
    }
    function bulkLaunchUrl(wrapperId,rootElements){
        // array of root elements can be provided
        if (rootElements) {
            return csrfUrl('/xapi/wrappers/'+wrapperId+'/bulklaunch?'+rootElements)
        } else {
            return csrfUrl('/xapi/wrappers/'+wrapperId+'/bulklaunch');
        }
    }
    function sessionUrl(){
        var sessionId = (XNAT.data.context.isImageSession) ? XNAT.data.context.ID : null;
        if (!sessionId) return false;
        return rootUrl('/REST/experiments/'+sessionId);
    }
    function fullScanPath(scanId){
        var sessionId = (XNAT.data.context.isImageSession) ? XNAT.data.context.ID : null;
        if (!sessionId) return false;
        return '/archive/experiments/'+sessionId+'/scans/'+scanId;
    }

    /*
     * Launcher UI Builder (i.e. CommandResolver)
     */

    /*
     * Panel form elements for launcher
     */

    var helptext = function(description) {
        return (description) ? '' : spawn('div.description',description);
    };
    var vertSpace = function(condensed) {
        return (condensed) ? '' : spawn('br.clear');
    };

    var defaultConfigInput = function(input){
        var name = input.name || input.label,
            value = input.value,
            label = input.label,
            description = input.description || '',
            required = input.required || false,
            classes = ['panel-input'],
            dataProps = {};
        value = (value === undefined || value === null || value == 'null') ? '' : value;
        label = label || name;
        description = description || '';

        if (required) {
            classes.push('required');
            description += ' (Required)';
        }

        return XNAT.ui.panel.input.text({
            name: name,
            value: value,
            description: description,
            label: label,
            data: dataProps,
            className: classes.join(' ')
        }).element;
    };

    var booleanEval = function(val){
        var trueValues = ['1','y','yes','true','t'];
        return (trueValues.indexOf(val.toString().toLowerCase()) >= 0 );
    };

    var configCheckbox = function(input){
        var name = input.name || input.outerLabel,
            value = input.value,
            checked = input.checked,
            boolean = input.boolean,
            outerLabel = input.outerLabel,
            innerLabel = input.innerLabel,
            description = input.description || '',
            required = input.required || false,
            condensed = input.condensed || false,
            classes = ['panel-element panel-input'],
            dataProps = { name: name },
            disabled = input.disabled || false,
            attr = {};

        if (input.children) {
            classes.push('parent-element');
            dataProps['children'] = input.children.join(',');
        }

        if (checked === 'checked') attr['checked'] = 'checked';
        if (disabled) attr['disabled'] = 'disabled';

        value = (boolean) ? 'true' : value;

        if (required) {
            classes.push('required');
            description += ' (Required)';
        }

        return spawn('div', { className: classes.join(' '), data: dataProps }, [
            spawn('label.element-label', outerLabel),
            spawn('div.element-wrapper', [
                spawn('label', [
                    spawn('input', { type: 'checkbox', name: name, value: value, attr: attr }),
                    innerLabel
                ]),
                helptext(description)
            ]),
            vertSpace(condensed)
        ]);
    };

    var configRadio = function(input){
        var name = input.name || input.outerLabel,
            value = input.value,
            checked = input.checked,
            boolean = input.boolean,
            outerLabel = input.outerLabel,
            innerLabel = input.innerLabel,
            description = input.description || '',
            required = input.required || false,
            condensed = input.condensed || false,
            classes = ['panel-element panel-input'],
            dataProps = { name: name },
            disabled = input.disabled || false,
            attr = {};

        if (input.children) {
            classes.push('parent-element');
            dataProps['children'] = input.children.join(',');
        }

        if (checked === 'true') attr['checked'] = 'checked';
        if (disabled) attr['disabled'] = 'disabled';

        if (required) {
            classes.push('required');
            description += ' (Required)';
        }

        return spawn('div', { className: classes.join(' '), data: dataProps }, [
            spawn('label.element-label', outerLabel),
            spawn('div.element-wrapper', [
                spawn('label', [
                    spawn('input', { type: 'radio', name: name, value: value, attr: attr }),
                    innerLabel
                ]),
                helptext(description)
            ]),
            vertSpace(condensed)
        ]);
    };

    var hiddenConfigInput = function(input) {
        var name = input.name || input.label,
            value = input.value,
            dataProps = {},
            attr = (input.disabled) ? { 'disabled':'disabled' } : {};

        return XNAT.ui.input.hidden({
            name: name,
            value: value,
            data: dataProps,
            attr: attr
        }).element;
    };

    var staticConfigInput = function(input) {
        var name = input.name || input.label,
            value = input.value,
            valueLabel = input.valueLabel,
            dataProps = { name: name },
            classes = ['panel-input','panel-element'],
            attr = (input.disabled) ? { 'disabled':'disabled' } : {};

        return spawn(
            'div', { className: classes.join(' '), data: dataProps }, [
                spawn('label.element-label', name),
                spawn('div.element-wrapper', { style: { 'word-wrap': 'break-word' } }, valueLabel),
                spawn('input',{
                    type: 'hidden',
                    name: name,
                    value: value,
                    data: dataProps,
                    attr: attr
                }),
                spawn('br.clear')
            ]
        );
    };

    var staticConfigList = function(name,list) {
        var listArray = list.split(',');
        if (listArray.length > 6) {
            return spawn(
                'div.panel-element', { data: { name: name } }, [
                    spawn('label.element-label', name),
                    spawn('div.element-wrapper', [
                        spawn('textarea',{ 'readonly':true, style: { height: '80px' }},listArray.join('\n'))
                    ]),
                    spawn('br.clear')
                ]
            )
        }
        else {
            listArray.forEach(function(item,i){
                listArray[i] = '<li>'+item+'</li>'
            });
            return spawn(
                'div.panel-element', { data: { name: name } }, [
                    spawn('label.element-label', name),
                    spawn('div.element-wrapper', [
                        spawn('ul',{ style: {
                            'list-style-type': 'none',
                            margin: 0,
                            padding: 0
                        }},listArray.join(''))
                    ]),
                    spawn('br.clear')
                ]
            )
        }
    };

    launcher.formInputs = function(input) {
        var formPanelElements = [];

        // create a panel.input for each input type
        switch (input.type) {
            case 'scanSelectMany':
                launcher.scanList.forEach(function (scan, i) {
                    var scanOpts = {
                        name: 'scan',
                        value: fullScanPath(scan.id),
                        innerLabel: scan.id + ' - ' + scan['series_description'],
                        condensed: true
                    };
                    if (i === 0) {
                        // first
                        scanOpts.outerLabel = 'scans';
                        formPanelElements.push(configCheckbox(scanOpts));
                    } else if (i < launcher.scanList.length - 1) {
                        // middle
                        formPanelElements.push(configCheckbox(scanOpts));
                    } else {
                        // last
                        scanOpts.condensed = false;
                        formPanelElements.push(configCheckbox(scanOpts));
                    }
                });
                break;
            case 'hidden':
                formPanelElements.push(hiddenConfigInput(input));
                break;
            case 'static':
                formPanelElements.push(staticConfigInput(input));
                break;
            case 'staticList':
                formPanelElements.push(staticConfigList(input.name, input.value));
                break;
            case 'checkbox':
                input.outerLabel = input.label;
                input.innerLabel = input.innerLabel || input.value;
                formPanelElements.push(configCheckbox(input));
                break;
            case 'radio':
                input.outerLabel = input.label;
                input.innerLabel = input.innerLabel || input.value;
                formPanelElements.push(configRadio(input));
                break;
            case 'boolean':
                input.boolean = true;
                input.outerLabel = input.label;
                input.innerLabel = input.innerLabel || 'True';
                input.checked = (booleanEval(input.value)) ? 'checked' : false;
                formPanelElements.push(configCheckbox(input));
                break;
            default:
                formPanelElements.push(defaultConfigInput(input));
        }

        return formPanelElements;

    };


    /*
     ** Launcher Options
     */

    function launchOneContainer(inputs,rootElement,wrapperId){

        var inputList = Object.keys(inputs);

        var launcherContent = spawn('div.panel',[
            spawn('p','Please specify settings for this container.'),
            spawn('div.standard-settings'),
            spawn('div.advanced-settings-container.hidden',[
                spawn('div.advanced-settings-toggle'),
                spawn('div.advanced-settings')
            ])
        ]);

        if (inputList.indexOf(rootElement) >= 0) {
            // if the root element is specified in the list of inputs ...


            XNAT.ui.dialog.open({
                title: 'Set Container Launch Values',
                content: launcherContent,
                width: 550,
                scroll: true,
                beforeShow: function(obj){
                    var $panel = obj.$modal.find('.panel');
                    var $standardInputContainer = $panel.find('.standard-settings');
                    var $advancedInputContainer = $panel.find('.advanced-settings');

                    // loop through each input and determine how to display it
                    // standard inputs with no children -- append the appropriate UI element
                    // standard inputs with children -- append the UI element and the child element(s) in a child element wrapper
                    // advanced inputs (that aren't children) -- append the UI element to the advanced input container

                    for (var i in inputs){

                        if (!inputs[i].parent || inputs[i].parent === undefined) {
                            // child inputs that specify a parent get special treatment
                            inputs[i].type = (inputs[i]['user-settable'] || i === rootElement) ? inputs[i].ui.default.type : 'hidden';
                            inputs[i].value = inputs[i].ui.default.values[0].value || inputs[i].value;
                            inputs[i].valueLabel = inputs[i].ui.default.values[0].label || '';

                            if (inputs[i].advanced === undefined || inputs[i].advanced !== true) {
                                var inputElement = launcher.formInputs(inputs[i]);
                                $standardInputContainer.append(inputElement);

                                if (inputs[i].children) {
                                    // child inputs are listed as an array of input ids

                                    var parentInput = inputs[i];
                                    var children = inputs[i].children;

                                    children.forEach(function(child){

                                        var useDefault = true;

                                        for (var k in inputs[child].ui) {
                                            // loop through each possible UI instance for all preset values for this child input.
                                            // append each child input in a special wrapper

                                            if (parentInput.ui[k] !== undefined) {
                                                useDefault = false; // if value-specific definitions are found, don't use the default

                                                if (parentInput.ui[k].values.length === 1) {
                                                    var childInput = inputs[child];
                                                    var classes = ['child-input'];
                                                    childInput.type = childInput.ui[k].type;
                                                    childInput.value = childInput.ui[k].values[0].value;
                                                    childInput.valueLabel = childInput.ui[k].values[0].label;
                                                    if (k !== parentInput.value) {
                                                        // if a preset value has been defined and does not match the default value of its parent input, then hide and disable this possible input.
                                                        childInput.disabled = true;
                                                        classes.push('hidden');
                                                    }

                                                    $standardInputContainer.append( spawn('div', { className: classes.join(' '), data: { preset: k }}, launcher.formInputs(childInput)) );
                                                }

                                                if (parentInput.ui[k].values.length > 1) {
                                                    // if more than one possible preset value is found for a child input,
                                                    // disregard the suggested input type and force a user selection
                                                    // generate a radio input for each possible value

                                                    var childInputs = [];

                                                    parentInput.ui[k].values.forEach(function(value){
                                                        var childInput = inputs[child];
                                                        var classes = ['child-input'];
                                                        childInput.type = 'radio';
                                                        childInput.value = childInput.ui[k].values[0].value;
                                                        childInput.valueLabel = childInput.ui[k].values[0].label;
                                                        if (k !== parentInput.value) {
                                                            // if a preset value has been defined and does not match the default value of its parent input, then hide and disable this possible input.
                                                            childInput.disabled = true;
                                                            classes.push('hidden');
                                                        }
                                                    });
                                                }
                                            }

                                            if (useDefault) {
                                                // if no value-specific settings are found, use the parent input's default values
                                                var childInput = inputs[child];
                                                var classes = ['child-input'];
                                                childInput.type = childInput.ui[k].type;
                                                if (childInput.ui[k].values.length) {
                                                    childInput.value = childInput.ui[k].values[0].value;
                                                    childInput.valueLabel = childInput.ui[k].values[0].label;
                                                } else {
                                                    childInput.value = childInput.valueLabel = '';
                                                }
                                                $standardInputContainer.append( spawn('div', { className: classes.join(' '), data: { preset: k }}, launcher.formInputs(childInput)) );
                                            }

                                        }

                                    });
                                }
                            }
                            if (inputs[i].advanced) {
                                var advancedInput = launcher.formInputs(inputs[i]);
                                $advancedInputContainer.append(advancedInput);
                                $advancedInputContainer.parents('.advanced-settings-container').removeClass('hidden');
                            }
                        }

                    }

                },
                buttons: [
                    {
                        label: 'Run Container',
                        isDefault: true,
                        close: false,
                        action: function(obj){
                            var $panel = obj.$modal.find('.panel'),
                                targetData = {};

                            // check all inputs for invalid characters
                            var $inputs = $panel.find('input'),
                                runContainer = true;
                            $inputs.each(function(){
                                var input = $(this)[0];
                                if (!launcher.noIllegalChars(input)) {
                                    runContainer = false;
                                    $(this).addClass('invalid');
                                }
                            });

                            if (runContainer) {
                                // gather form input values
                                targetData[rootElement] = $panel.find('input[name='+rootElement+']').val();

                                $panel.find('input').not(':disabled').not('[type=checkbox]').not('[type=radio]').not('[name='+rootElement+']').each(function(){
                                    // get the name and value from each text element and add it to our data to post
                                    var key = $(this).prop('name');
                                    targetData[key] = $(this).val();
                                });

                                $panel.find('input[type=checkbox]').not(':disabled').each(function(){
                                    var key = $(this).prop('name');
                                    var val = ($(this).is(':checked')) ? $(this).val() : false;
                                    targetData[key] = val;
                                });

                                $panel.find('select').not(':disabled').each(function(){
                                    var key = $(this).prop('name');
                                    var val = $(this).find('option:selected').val();
                                    targetData[key] = val;
                                });

                                var dataToPost = targetData;

                                xmodal.loading.open({ title: 'Launching Container...' });

                                XNAT.xhr.postJSON({
                                    url: containerLaunchUrl(wrapperId),
                                    data: JSON.stringify(dataToPost),
                                    success: function(data){
                                        xmodal.loading.close();
										var messageContent;
										if (data.status === 'success') {
												if ( data['type'] === 'service') {
													messageContent = spawn('p',{ style: { 'word-wrap': 'break-word'}}, 'Service ID: '+data['service-id']);
												}else {
													messageContent = spawn('p',{ style: { 'word-wrap': 'break-word'}}, 'Container ID: '+data['container-id']);
											    }
										}else {
											messageContent = spawn('p', data.message);
										}
                                        XNAT.ui.dialog.open({
                                            title: 'Container Launch <span style="text-transform: capitalize">'+data.status+'</span>',
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
                                    },
                                    fail: function (e) {
                                        xmodal.loading.close();

                                        if (e.responseJSON.message) {
                                            var data = e.responseJSON;
                                            var messageContent = spawn('div',[
                                                spawn('p',{ style: { 'font-weight': 'bold' }}, 'Error Message:'),
                                                spawn('pre.json', data.message),
                                                spawn('p',{ style: { 'font-weight': 'bold' }}, 'Parameters Submitted To XNAT:'),
                                                spawn('div', prettifyJSON(data.params))
                                            ]);

                                            XNAT.ui.dialog.open({
                                                title: 'Container Launch <span style="text-transform: capitalize">'+data.status+'</span>',
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

        } else {
            errorHandler({
                responseText: 'Could not launch command. Root element "'+rootElement+'" not found in the list of inputs provided.'
            });
        }
    }


//    function launchManyContainers(inputArray,rootElement,wrapperId,targets){
        /* In a bulk launcher, a list of input objects will be passed to the launcher.
         * The launcher should consider the target elements to be static
         * (i.e. once selected and sent to the bulk launcher, the user shouldn't be re-selecting them)
         * Also, any child elements of the root element should also be set to their default values.
         * Users should be able to set other inputs in bulk for all selected root elements
         * If there are child elements of non-root inputs, they will be treated as standard inputs so they can be bulk-settable
         * After the user makes their selections, a bulk object is assembled from the inputs and sent to the bulk launcher
         */

/*        var inputList = Object.keys(inputArray[0]);

        var launcherContent = spawn('div.panel',[
            spawn('p','Please specify settings for this container.'),
            spawn('div.target-list')
        ]);

        if ( inputList.indexOf(rootElement) >=0 ) { // if the specified root element matches an input parameter, we can proceed

            XNAT.ui.dialog.open({
                title: 'Set Container Launch Values',
                content: launcherContent,
                width: 550,
                scroll: true,
                beforeShow: function(obj){
                    var $panel = obj.$modal.find('.panel'),
                        $targetListContainer = $panel.find('.target-list');

                    // display root elements first
                    $targetListContainer.append(spawn('p',[ spawn('strong', targets.length + ' item(s) selected to run in bulk.' )]));

                    var targetList = launcher.formInputs({ name: rootElement, type: 'staticList', value: targets.toString() });
                    $targetListContainer.append(targetList);

                    // loop through each input and determine how to display it
                    // root element -- create hidden inputs
                    // child element of root element -- create hidden inputs
                    // standard inputs with no children -- append the appropriate UI element
                    // standard inputs with children -- append the UI element and the child element(s) in a child element wrapper
                    // advanced inputs (that aren't children) -- append the UI element to the advanced input container

                    inputArray.forEach(function(inputs,k){
                        // iterate through each list of inputs.

                        if (k===0) {
                            // on first iteration, create all user-settable inputs

                            $panel.append(spawn('div',{ className: 'bulk-master bulk-inputs inputs-'+k },[
                                spawn('div.standard-settings'),
                                spawn('div.advanced-settings-container.hidden',[
                                    spawn('div.advanced-settings-toggle'),
                                    spawn('div.advanced-settings')
                                ])
                            ]));

                            var $standardInputContainer = $panel.find('.standard-settings'),
                                $advancedInputContainer = $panel.find('.advanced-settings');

                            for (var i in inputs) {
                                // child inputs that specify a parent get special treatment
                                if ((!inputs[i].parent || inputs[i].parent === undefined)) {
                                    // don't display the root element input again ... it has already been listed.
                                    inputs[i].type = (!inputs[i]['user-settable'] || i === rootElement) ? 'hidden' : inputs[i].ui.default.type;
                                    inputs[i].value = inputs[i].ui.default.values[0].value || inputs[i].value;
                                    inputs[i].valueLabel = inputs[i].ui.default.values[0].label || '';

                                    if (inputs[i].advanced === undefined || inputs[i].advanced !== true) {
                                        var inputElement = launcher.formInputs(inputs[i]);
                                        $standardInputContainer.append(inputElement);

                                        if (inputs[i].children) {
                                            // child inputs are listed as an array of input ids

                                            var parentInput = inputs[i];
                                            var children = inputs[i].children;

                                            children.forEach(function(child){

                                                var useDefault = true;

                                                for (var k in inputs[child].ui) {
                                                    // loop through each possible UI instance for all preset values for this child input.
                                                    // append each child input in a special wrapper

                                                    if (parentInput.ui[k] !== undefined) {
                                                        useDefault = false; // if value-specific definitions are found, don't use the default

                                                        var childInput = inputs[child];
                                                        var classes = ['child-input'];
                                                        childInput.type = 'hidden';
                                                        childInput.value = childInput.ui[k].values[0].value;
                                                        childInput.valueLabel = childInput.ui[k].values[0].label;
                                                        if (k !== parentInput.value) {
                                                            // if a preset value has been defined and does not match the default value of its parent input, then hide and disable this possible input.
                                                            childInput.disabled = true;
                                                            classes.push('hidden');
                                                        }
                                                        $standardInputContainer.append( spawn('div', { className: classes.join(' '), data: { preset: k }}, launcher.formInputs(childInput)) );
                                                    }

                                                    if (useDefault) {
                                                        // if no value-specific settings are found, use the parent input's default values
                                                        var childInput = inputs[child];
                                                        var classes = ['child-input'];
                                                        childInput.type = 'hidden';
                                                        if (childInput.ui[k].values.length) {
                                                            childInput.value = childInput.ui[k].values[0].value;
                                                            childInput.valueLabel = childInput.ui[k].values[0].label;
                                                        } else {
                                                            childInput.value = childInput.valueLabel = '';
                                                        }
                                                        $standardInputContainer.append( spawn('div', { className: classes.join(' '), data: { preset: k }}, launcher.formInputs(childInput)) );
                                                    }

                                                }

                                            });
                                        }
                                    }
                                    if (inputs[i].advanced) {
                                        var advancedInput = launcher.formInputs(inputs[i]);
                                        $advancedInputContainer.append(advancedInput);
                                        $advancedInputContainer.parents('.advanced-settings-container').removeClass('hidden');
                                    }
                                }

                            }


                        } else {
                            // on 2nd - nth inputs, simply create hidden inputs whose values will be toggled by user changes to first set of inputs
                            $panel.append(spawn('div',{ className: 'bulk-controls bulk-inputs inputs-'+k }));
                            var $bulkInputContainer = $panel.find('.inputs-'+k);

                            for (var i in inputs) {
                                if (!inputs[i].parent || inputs[i].parent === undefined) {
                                    // child inputs that specify a parent get special treatment
                                    inputs[i].type = 'hidden';
                                    inputs[i].value = inputs[i].ui.default.values[0].value || inputs[i].value;

                                    // handle non-standard boolean input values in bulk hidden inputs
                                    if (inputs[i].ui.default.type === 'boolean') {
                                        inputs[i].value = booleanEval(inputs[i].value)
                                    }

                                    var inputElement = launcher.formInputs(inputs[i]);
                                    $bulkInputContainer.append(inputElement);

                                    if (inputs[i].children) {
                                        // child inputs are listed as an array of input ids

                                        var parentInput = inputs[i];
                                        var children = inputs[i].children;

                                        children.forEach(function(child){

                                            var useDefault = true;

                                            for (var k in inputs[child].ui) {
                                                // loop through each possible UI instance for all preset values for this child input.
                                                // append each child input in a special wrapper

                                                if (parentInput.ui[k] !== undefined) {
                                                    useDefault = false; // if value-specific definitions are found, don't use the default

                                                    var childInput = inputs[child];
                                                    childInput.type = 'hidden';
                                                    childInput.value = childInput.ui[k].values[0].value;
                                                    if (k !== parentInput.value) {
                                                        // if a preset value has been defined and does not match the default value of its parent input, then hide and disable this possible input.
                                                        childInput.disabled = true;
                                                    }
                                                    $bulkInputContainer.append( launcher.formInputs(childInput) );
                                                }

                                                if (useDefault) {
                                                    // if no value-specific settings are found, use the parent input's default values
                                                    var childInput = inputs[child];
                                                    childInput.type = 'hidden';
                                                    if (childInput.ui[k].values.length) {
                                                        childInput.value = childInput.ui[k].values[0].value;
                                                    } else {
                                                        childInput.value = '';
                                                    }
                                                    $bulkInputContainer.append( launcher.formInputs(childInput) );
                                                }
                                            }
                                        });
                                    }
                                }

                            }

                        }

                    });

                },
                buttons: [
                    {
                        label: 'Run Container(s)',
                        isDefault: true,
                        close: false,
                        action: function(obj){
                            var $panel = obj.$modal.find('.panel'),
                                bulkData = [];

                            // check all inputs for invalid characters
                            var $inputs = $panel.find('input'),
                                runContainer = true;
                            $inputs.each(function(){
                                var input = $(this)[0];
                                if (!launcher.noIllegalChars(input)) {
                                    runContainer = false;
                                    $(this).addClass('invalid');
                                }
                            });

                            if (runContainer) {
                                $panel.find('.bulk-inputs').each(function(){
                                    // iterate over each set of inputs and add an object of inputs and values to the bulkData array
                                    var targetData = {},
                                        $thisPanel = $(this);

                                    // gather form input values
                                    targetData[rootElement] = $thisPanel.find('input[name='+rootElement+']').val();

                                    $thisPanel.find('input').not(':disabled').not('[type=checkbox]').not('[type=radio]').not('[name='+rootElement+']').each(function(){
                                        // get the name and value from each text element and add it to our data to post
                                        var key = $(this).prop('name');
                                        targetData[key] = $(this).val();
                                    });

                                    $thisPanel.find('input[type=checkbox]').not(':disabled').each(function(){
                                        var key = $(this).prop('name');
                                        var val = ($(this).is(':checked')) ? $(this).val() : false;
                                        targetData[key] = val;
                                    });

                                    $thisPanel.find('select').not(':disabled').each(function(){
                                        var key = $(this).prop('name');
                                        var val = $(this).find('option:selected').val();
                                        targetData[key] = val;
                                    });

                                    bulkData.push(targetData);
                                });

                                var dataToPost = bulkData;

                                xmodal.loading.open({ title: 'Launching Container(s)...' });

                                XNAT.xhr.postJSON({
                                    url: bulkLaunchUrl(wrapperId),
                                    data: JSON.stringify(dataToPost),
                                    success: function(data){
                                        xmodal.loading.close();

                                        // bulk launch success returns two arrays -- containers that successfully launched, and containers that failed to launch
                                        var messageContent = [],
                                            totalLaunchAttempts = data.successes.concat(data.failures).length;
                                        if (data.failures.length > 0) {
                                            messageContent.push( spawn('div.message',data.successes.length + ' of '+totalLaunchAttempts+' containers successfully launched.') );
                                        } else if(data.successes.length > 0) {
                                            messageContent.push( spawn('div.success','All containers successfully launched.') );
                                        } else {
                                            errorHandler({
                                                statusText: 'Something went wrong. No containers were launched.'
                                            });
                                        }

                                        if (data.successes.length > 0) {
                                            messageContent.push( spawn('h3',{'style': {'margin-top': '2em' }},'Successful Container Launches') );

                                            data.successes.forEach(function(success){
												if (success['type'] === 'service') {
													messageContent.push( spawn('p',[spawn('strong','Service ID: '),spawn('span',success['service-id']) ]));
												}else {
													messageContent.push( spawn('p',[
														spawn('strong','Container ID: '),
														spawn('span',success['container-id'])
													]) );
												}
                                                messageContent.push( spawn('div',prettifyJSON(success.params)) );
                                            });
                                        }

                                        if (data.failures.length > 0){
                                            messageContent.push( spawn('h3',{'style': {'margin-top': '2em' }},'Failed Container Launches') );
                                            data.failures.forEach(function(failure){
                                                messageContent.push( spawn('p',{ style: { 'font-weight': 'bold' }}, 'Error Message:') );
                                                messageContent.push( spawn('pre.json', failure.message) );
                                                messageContent.push( spawn('div',prettifyJSON(failure.params)) );
                                            });
                                        }

                                        XNAT.ui.dialog.open({
                                            title: 'Container Launch Success',
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
                                    },
                                    fail: function (e) {
                                        xmodal.loading.close();

                                        if (e.responseJSON.message) {
                                            var data = e.responseJSON;
                                            var messageContent = spawn('div',[
                                                spawn('p',{ style: { 'font-weight': 'bold' }}, 'Error Message:'),
                                                spawn('pre.json', data.message),
                                                spawn('p',{ style: { 'font-weight': 'bold' }}, 'Parameters Submitted To XNAT:'),
                                                spawn('div', prettifyJSON(data.params))
                                            ]);

                                            XNAT.ui.dialog.open({
                                                title: 'Container Launch <span style="text-transform: capitalize">'+data.status+'</span>',
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


        } else {
            errorHandler({
                statusText: 'Root element mismatch',
                responseText: 'No instance of '+rootElement+' was found in the list of inputs for this command'
            });
        }
    }
*/

    
    function launchManyContainers(inputJson,rootElement,wrapperId,targets, targetLabels){
        /* In a bulk launcher, a list of input objects will be passed to the launcher.
         * The launcher should consider the target elements to be static
         * (i.e. once selected and sent to the bulk launcher, the user shouldn't be re-selecting them)
         * Also, any child elements of the root element should also be set to their default values.
         * Users should be able to set other inputs in bulk for all selected root elements
         * If there are child elements of non-root inputs, they will be treated as standard inputs so they can be bulk-settable
         * After the user makes their selections, a bulk object is assembled from the inputs and sent to the bulk launcher
         */

    	//inputJson contains elements like
    	//"session":{"description":"Input session","type":"Session","default-value":null,"matcher":null,"user-settable":null,"advanced":false,"required":null}
 
        var launcherContent = spawn('div.panel',[
            spawn('p','Please specify settings for this container.'),
            spawn('div.target-list')
        ]);

        XNAT.ui.dialog.open({
                title: 'Set Container Launch Values',
                content: launcherContent,
                width: 550,
                scroll: true,
                beforeShow: function(obj){
                    var $panel = obj.$modal.find('.panel'),
                        $targetListContainer = $panel.find('.target-list');

                    // display root elements first
                    $targetListContainer.append(spawn('p',[ spawn('strong', targets.length + ' item(s) selected to run in bulk.' )]));

                    var targetList = launcher.formInputs({ name: rootElement, type: 'staticList', value: targets.toString() });
                    $targetListContainer.append(targetList);
                    var k=0;
                    for(var argument in inputJson) {
              		  if (inputJson.hasOwnProperty(argument)) {
              		        var argumentDefinition = inputJson[argument];
              		        if (argumentDefinition['user-settable']) {
                                $panel.append(spawn('div',{ className: 'bulk-controls bulk-inputs inputs-'+k }));
                                var $bulkInputContainer = $panel.find('.inputs-'+k);

                  		        var input = {};	
                  		            input.name = argument;
                                	input.label = argument;
                                    for(var property in argumentDefinition) {
                                		  if (argumentDefinition.hasOwnProperty(property)) {
                                		    var propertyValue = argumentDefinition[property];
                                		    input[property] = propertyValue; 
                                		  }
                                    }
                                	input.type = argumentDefinition.type;
                                    input.value =  argumentDefinition['default-value'];
                                        if (input.advanced === undefined || input.advanced !== true) {
                                            var inputElement = launcher.formInputs(input);
                                            $bulkInputContainer.append(inputElement);
                                         }
                                        else if (input.advanced) {
                                            var advancedInput = launcher.formInputs(input);
                                            $bulkInputContainer.append(advancedInput);
                                           // $bulkInputContainer.parents('.advanced-settings-container').removeClass('hidden');
                                        }
                              ++k;          
              		        }else {
              		        	if (argument === rootElement) {
                                    $panel.append(spawn('div',{ className: 'bulk-controls bulk-inputs inputs-'+k }));
                                    var $bulkInputContainer = $panel.find('.inputs-'+k);
              		        		var input = {};	
                  		            input.name = argument;
                                	input.label = argument;
                                    for(var property in argumentDefinition) {
                                		  if (argumentDefinition.hasOwnProperty(property)) {
                                		    var propertyValue = argumentDefinition[property];
                                		    input[property] = propertyValue; 
                                		  }
                                    }
                                	input.type =  'hidden' ;
                                    input.value =  targets.toString();
                                    if (input.advanced === undefined || input.advanced !== true) {
                                        var inputElement = launcher.formInputs(input);
                                        $bulkInputContainer.append(inputElement);
                                     }
                                    ++k;
              		        	}
              		        }
              		  }
                    }
                },
                buttons: [
                    {
                        label: 'Run Container(s)',
                        isDefault: true,
                        close: false,
                        action: function(obj){
                            var $panel = obj.$modal.find('.panel'),
                                bulkData = [];

                            // check all inputs for invalid characters
                            var $inputs = $panel.find('input'),
                                runContainer = true;
                            $inputs.each(function(){
                                var input = $(this)[0];
                                if (!launcher.noIllegalChars(input)) {
                                    runContainer = false;
                                    $(this).addClass('invalid');
                                }
                            });

                            if (runContainer) {
                       		    var csvRootElementValues = $panel.find('input[name='+rootElement+']').val();
                       		    var rootElementArray = csvRootElementValues.split(',');
                       		    var targetData = {};
                       		    $panel.find('.bulk-inputs').each(function(){
                                    // iterate over each set of inputs and add an object of inputs and values to the bulkData array
                       		    	var $thisPanel = $(this);

                                    $thisPanel.find('input').not(':disabled').not('[type=checkbox]').not('[type=radio]').not('[name='+rootElement+']').each(function(){
                                        // get the name and value from each text element and add it to our data to post
                                        var key = $(this).prop('name');
                                        targetData[key] = $(this).val();
                                    });

                                    $thisPanel.find('input[type=checkbox]').not(':disabled').each(function(){
                                        var key = $(this).prop('name');
                                        var val = ($(this).is(':checked')) ? $(this).val() : false;
                                        targetData[key] = val;
                                    });

                                    $thisPanel.find('select').not(':disabled').each(function(){
                                        var key = $(this).prop('name');
                                        var val = $(this).find('option:selected').val();
                                        targetData[key] = val;
                                    });

                                });
                       		    rootElementArray.forEach(function(item,i){
                           		    var launchData = {};
                           		    launchData[rootElement] = item;	
                           		    Object.keys(targetData).forEach(function(key) {
                           		      launchData[key] = targetData[key];
                           		    });	
                           		    bulkData.push(launchData);
                       		    }); 	

                                var dataToPost = bulkData;

                                xmodal.loading.open({ title: 'Launching Container(s)...' });

                                XNAT.xhr.postJSON({
                                    url: bulkLaunchUrl(wrapperId),
                                    data: JSON.stringify(dataToPost),
                                    success: function(data){
                                        xmodal.loading.close();

                                        // bulk launch success returns two arrays -- containers that successfully launched, and containers that failed to launch
                                        var messageContent = [],
                                            totalLaunchAttempts = data.successes.concat(data.failures).length;
                                        if (data.failures.length > 0) {
                                            messageContent.push( spawn('div.message',data.successes.length + ' of '+totalLaunchAttempts+' containers successfully launched.') );
                                        } else if(data.successes.length > 0) {
                                            messageContent.push( spawn('div.success','All containers successfully launched.') );
                                        } else {
                                            errorHandler({
                                                statusText: 'Something went wrong. No containers were launched.'
                                            });
                                        }

                                        if (data.successes.length > 0) {
                                            messageContent.push( spawn('h3',{'style': {'margin-top': '2em' }},'Successful Container Launches') );

                                            data.successes.forEach(function(success){
												if (success['type'] === 'service') {
													messageContent.push( spawn('p',[spawn('strong','Service ID: '),spawn('span',success['service-id']) ]));
												}else {
													messageContent.push( spawn('p',[
														spawn('strong','Container ID: '),
														spawn('span',success['container-id'])
													]) );
												}
                                                messageContent.push( spawn('div',prettifyJSON(success.params)) );
                                            });
                                        }

                                        if (data.failures.length > 0){
                                            messageContent.push( spawn('h3',{'style': {'margin-top': '2em' }},'Failed Container Launches') );
                                            data.failures.forEach(function(failure){
                                                messageContent.push( spawn('p',{ style: { 'font-weight': 'bold' }}, 'Error Message:') );
                                                messageContent.push( spawn('pre.json', failure.message) );
                                                messageContent.push( spawn('div',prettifyJSON(failure.params)) );
                                            });
                                        }

                                        XNAT.ui.dialog.open({
                                            title: 'Container Launch Success',
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
                                    },
                                    fail: function (e) {
                                        xmodal.loading.close();

                                        if (e.responseJSON.message) {
                                            var data = e.responseJSON;
                                            var messageContent = spawn('div',[
                                                spawn('p',{ style: { 'font-weight': 'bold' }}, 'Error Message:'),
                                                spawn('pre.json', data.message),
                                                spawn('p',{ style: { 'font-weight': 'bold' }}, 'Parameters Submitted To XNAT:'),
                                                spawn('div', prettifyJSON(data.params))
                                            ]);

                                            XNAT.ui.dialog.open({
                                                title: 'Container Launch <span style="text-transform: capitalize">'+data.status+'</span>',
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


    
    // for bulk launching, apply any user-updated value to all matching inputs
    $(document).on('change','.bulk-master input',function(){
        var name = $(this).prop('name');
        if ($(this).prop('type') === 'checkbox' && !$(this).is(':checked')) {
            $('.bulk-controls').find('input[name='+name+']').val('false');
        } else {
            var changedVal = $(this).val();
            $('.bulk-controls').find('input[name='+name+']').val(changedVal);
        }
    });

    launcher.defaultLauncher = function(wrapperId,rootElement,rootElementValue){
        rootElementValue = rootElementValue || XNAT.data.context.ID; // if no value is provided, assume that the current page context provides the value.

        if (!rootElementValue) {
            errorHandler({ responseText: 'Could not launch UI. No value provided for '+rootElement+'.' });
            return false;
        }

        xmodal.loading.open({ title: 'Configuring Container Launcher' });
        var launchUrl = (projectId) ? getProjectLauncherUI(wrapperId,rootElement,rootElementValue) : getLauncherUI(wrapperId,rootElement,rootElementValue);

        XNAT.xhr.getJSON({
            url: launchUrl,
            fail: function(e){
                xmodal.loading.close();
                errorHandler({
                    statusText: e.statusText,
                    responseText: 'Could not launch UI with value: "'+rootElementValue+'" for root element: "'+rootElement+'".'
                });
            },
            success: function(data){
                xmodal.loading.close();
                var inputs = data.inputs;
                launchOneContainer(inputs,rootElement,wrapperId);
            }
        })
    };

    launcher.bulkSelectDialog = function(commandId,wrapperId,rootElement){
        // get command definition
        var launcherConfig = launcher.getConfig(commandId,wrapperId),
            advancedInputs = {},
            normalInputs = {};

        var inputs = launcherConfig.inputs;

        var inputList = Object.keys(inputs);
        if ( inputList.indexOf(rootElement) >= 0 ) { // if the specified root element matches an input parameter, we can proceed

            inputs[rootElement].type = 'scanSelectMany';

            // separate normal inputs from advanced inputs
            for (var i in inputs) {
                if (paramObj[i].advanced) {
                    advancedInputs[i] = paramObj[i];
                } else {
                    normalInputs[i] = paramObj[i];
                }
            }

            // open the template form in a modal
            XNAT.ui.dialog.open({
                title: 'Set Config Values',
                content: spawn('div.panel'),
                width: 550,
                scroll: true,
                nuke: true,
                beforeShow: function (obj) {
                    var $panel = obj.$modal.find('.panel');
                    $panel.spawn('p', 'Please specify settings for this container.');

                    // place normal and advanced input form elements into the template
                    $panel.append(launcher.formInputs(normalInputs));

                    if (Object.keys(advancedInputs).length > 0) {
                        $panel.spawn('p','Advanced Inputs');
                        $panel.append(launcher.formInputs(advancedInputs,true));
                    }
                },
                buttons: [
                    {
                        label: 'Run Container',
                        close: false,
                        isDefault: true,
                        action: function (obj) {
                            var $panel = obj.$modal.find('.panel');
                            var selectedItems = $panel.find('input[name='+rootElement+']:checked');
                            if (selectedItems.length === 0) {
                                XNAT.ui.banner.top(2000,'<b>Error:</b> You must select at least one '+rootElement, 'error');
                                return false;
                            } else {
                                var dataToPost = [];
                                $(selectedItems).each(function(){
                                    var itemsToPost = {};
                                    itemsToPost[rootElement] = $(this).val();

                                    $panel.find('input').not('[type=checkbox]').not('[type=radio]').each(function () {
                                        // get the name and value from each text element and add it to our data to post
                                        var key = $(this).prop('name');
                                        itemsToPost[key] = $(this).val();
                                    });

                                    $panel.find('input[type=checkbox]').not('input[name='+rootElement+']').each(function () {
                                        var key = $(this).prop('name');
                                        var val = ($(this).is(':checked')) ? $(this).val() : false;
                                        itemsToPost[key] = val;
                                    });

                                    $panel.find('select').each(function () {
                                        var key = $(this).prop('name');
                                        var val = $(this).find('option:selected').val();
                                        itemsToPost[key] = val;
                                    });

                                    dataToPost.push(itemsToPost);
                                });

                                XNAT.xhr.postJSON({
                                    url: bulkLaunchUrl(wrapperId),
                                    data: JSON.stringify(dataToPost),
                                    success: function(data){
                                        xmodal.loading.close();

                                        var successRecord = '',
                                            failureRecord = '';

                                        if (data.successes.length > 0) {
                                            successRecord += '<p>Successfully launched containers on: </p><ul>';
                                            data.successes.forEach(function(success){
                                                successRecord += '<li>'+success.params[rootElement]+'</li>';
                                            });
                                            successRecord += '</ul>';
                                        }

                                        if (data.failures.length > 0) {
                                            failureRecord += '<p>Failed to launch containers on: </p><ul>';
                                            data.failures.forEach(function(failure){
                                                failureRecord += '<li>'+failure.params[rootElement]+'</li>';
                                            });
                                            failureRecord += '</ul>';
                                        }

                                        XNAT.ui.dialog.open({
                                            title: 'Container Launch Record',
                                            content: successRecord + failureRecord,
                                            buttons: [
                                                {
                                                    label: 'OK',
                                                    isDefault: true,
                                                    close: true,
                                                    action: XNAT.ui.dialog.closeAll()
                                                }
                                            ]
                                        })
                                    },
                                    fail: function (e) {
                                        errorHandler(e);
                                    }
                                });
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
        } else {
            errorHandler({
                statusText: 'Root element mismatch',
                responseText: 'No instance of ' + rootElement + ' was found in the list of inputs for this command'
            });
        }

    };

    launcher.singleScanDialog = function(wrapperId,rootElementPath){
        // end goal is submitting to /xapi/commands/launch/
        // need to build UI with input values from /xapi/wrappers/{id}/launchui, specifying the root element name and path

        var launchUrl = (projectId) ? getProjectLauncherUI(wrapperId,'scan',rootElementPath) : getLauncherUI(wrapperId, 'scan', rootElementPath);

        XNAT.xhr.getJSON({
            url: launchUrl,
            fail: function(e){
                errorHandler(e);
            },
            success: function(data){
                var inputs = data.inputs;
                var rootElement = 'scan';
                launchOneContainer(inputs,rootElement,wrapperId);
            }
        });
    };

/*
 
     launcher.bulkLaunchDialog = function(wrapperId,rootElement,targets){
        // 'targets' should be formatted as a one-dimensional array of XNAT data values (i.e. scan IDs) that a container will run on in series.
        // the 'root element' should match one of the inputs in the command config object, and overwrite it with the values provided in the 'targets' array

        if (!targets || targets.length === 0) return false;
        var targetObj = rootElement + '=' + targets.toString();
        var launchUrl = (projectId) ?
            rootUrl('/xapi/projects/'+projectId+'/wrappers/'+wrapperId+'/bulklaunch?'+targetObj) :
            rootUrl('/xapi/wrappers/'+wrapperId+'/bulklaunch?'+targetObj);

        xmodal.loading.open({ title: 'Configuring Container Launcher' });
        XNAT.xhr.getJSON({
            url: launchUrl,
            fail: function(e){
                xmodal.loading.close();
                errorHandler({
                    statusText: e.statusText,
                    responseText: 'Could not launch UI with value(s): "'+targets.toString()+'" for root element: "'+rootElement+'".'
                });
            },
            success: function(data){
                xmodal.loading.close();
                var inputs = data.inputs;
                launchManyContainers(inputs,rootElement,wrapperId,targets);
            }
        });
    };
*/

    
    launcher.bulkLaunchDialog = function(project,commandId,wrapperId,rootElement,targets, targetLabels){
        // 'targets' should be formatted as a one-dimensional array of XNAT data values (i.e. scan IDs) that a container will run on in series.
        // the 'root element' should match one of the inputs in the command config object, and overwrite it with the values provided in the 'targets' array

        if (!targets || targets.length === 0) return false;
        var targetObj = rootElement + '=' + targets.toString();

        var launchUrl = rootUrl('/xapi/projects/'+project+'/commands/'+commandId+'/wrappers/'+wrapperId+'/config');
            
        xmodal.loading.open({ title: 'Configuring Container Launcher' });
        XNAT.xhr.getJSON({
            url: launchUrl,
            fail: function(e){
                xmodal.loading.close();
                errorHandler({
                    statusText: e.statusText,
                    responseText: 'Could not launch UI with value(s): "'+targets.toString()+'" for root element: "'+rootElement+'".'
                });
            },
            success: function(data){
                xmodal.loading.close();
                var inputs = data.inputs;
                launchManyContainers(inputs,rootElement,wrapperId,targets,targetLabels);
            }
        });
    };

    launcher.terminateDialog = function(targets){
        // 'targets' should be formatted as a one-dimensional array of XNAT data values (i.e. scan IDs) that a container will run on in series.

        if (!targets || targets.length === 0) return false;
        var terminateUrl = rootUrl('/xapi/workflows/killrunning'+'?accessionid='+targets.toString());

        xmodal.loading.open({ title: 'Preparing to terminate running jobs' });
		XNAT.xhr.postJSON({
			url: terminateUrl,
			success: function(data){
				xmodal.loading.close();
			   XNAT.ui.dialog.open({
					title: 'Container Termination Success',
					content: spawn('div', data ),
					buttons: [
						{
							label: 'OK',
							isDefault: true,
							close: true,
							action: XNAT.ui.dialog.closeAll()
						}
					]
				});
             },
             fail: function (e) {
					xmodal.loading.close();
					XNAT.ui.dialog.open({
						title: 'Container Termination <span style="text-transform: capitalize">'+e.status+'</span>',
						content: e.message,
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
		});

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

    /*
     * Build UI for menu selection
     */

    launcher.containerMenuItems = containerMenuItems = [
        {
            text: 'Run Containers',
            url: '#run',
            submenu: {
                id: 'containerSubmenuItems',
                itemdata: [
                ]
            }
        }
    ];

    launcher.addMenuItem = function(command,commandSet){
        commandSet = commandSet || [];
        var label = (command['wrapper-description'].length) ?
            command['wrapper-description'] :
            command['wrapper-name'];

        if (command.enabled){
            commandSet.push(
                spawn('li', [
                    spawn('a', {
                        html: label,
                        href: '#!',
                        className: 'commandLauncher',
                        data: {
                            wrapperid: command['wrapper-id'],
                            rootElementName: command['root-element-name'],
                            uri: command['uri'],
                            launcher: command['launcher']
                        }
                    })
                ]));
        }
        return commandSet;
    };

    launcher.createMenu = function(target,commandSet){
        /*
        var containerMenu = spawn('li.has-submenu',[
            spawn(['a',{ href: '#!', html: 'Run' }]),
            spawn(['ul.dropdown-submenu', itemSet ])
        ]);
        */

        target.append(commandSet);
    };

    /* to be replaced when we kill YUI */
    launcher.addYUIMenuItem = function(command){
        if (command.enabled) {
            var launcher = command.launcher || "default";
            var label = (command['wrapper-description'].length) ?
                command['wrapper-description'] :
                command['wrapper-name'];
            containerMenuItems[0].submenu.itemdata.push({
                text: label,
                url: 'javascript:openCommandLauncher({ wrapperid:"'+command['wrapper-id']+'", launcher: "'+launcher+'", rootElement: "'+ command['root-element-name'] + '" })',
                classname: 'enabled wrapped' // injects a custom classname onto the surrounding li element.
            });
        }
    };

    launcher.createYUIMenu = function(target){
        target = target || 'actionsMenu';
        var containerMenu = new YAHOO.widget.Menu('containerMenu', { autosubmenudisplay:true, scrollincrement:5, position:'static' });
        containerMenu.addItems(containerMenuItems);
        if (containerMenuItems[0].submenu.itemdata.length > 0) {
            containerMenu.render(target);
        }
    };

    launcher.init = function() {
        // populate or hide the command launcher based on what's in context
        XNAT.xhr.getJSON({
            url: rootUrl('/xapi/commands/available?project=' + projectId + '&xsiType=' + xsiType),
            success: function (data) {
                var availableCommands = data;
                if (!availableCommands.length) {
                    return false;
                } else {
                    var spawnedCommands = [];
                    availableCommands.forEach(function (command) {
                        launcher.addYUIMenuItem(command);
                    });
                    launcher.createYUIMenu('actionsMenu',spawnedCommands);
                }

            },
            fail: function (e) {
                errorHandler(e);
            }
        });

        // Special case: If this is a session, run a second context check for scans
        // only support scan-level actions if the new scan table is found.
        if (XNAT.data.context.isImageSession && document.getElementById('selectable-table-scans')) {
            var xsiScanType = xsiType.replace('Session','Scan');

            XNAT.xhr.getJSON({
                url: rootUrl('/xapi/commands/available?project=' + projectId + '&xsiType=' + xsiScanType),
                success: function (data) {
                    var availableCommands = data;
                    if (!availableCommands.length) {
                        return false;
                    } else {
                        // build menu of commands
                        var spawnedCommands = [];
                        availableCommands.forEach(function (command) {
                            command.launcher = 'multiple-scans';
                            command.uri = '';
                            launcher.addMenuItem(command,spawnedCommands);
                        });

                        // add action menu to each scan listing
                        launcher.scanList = XNAT.data.context.scans || [];
                        launcher.scanList.forEach(function(scan){
                            var scanCommands = [];
                            availableCommands.forEach(function (command) {
                                command.launcher = 'single-scan';
                                command.uri = fullScanPath(scan['id']);
                                launcher.addMenuItem(command,scanCommands);
                            });

                            if (scanCommands.length > 0){
                                var scanActionTarget = $('tr#scan-'+scan['id']).find('.single-scan-actions-menu');
                                scanActionTarget.append(scanCommands)
                                $('.run-menu').show();
                            }
                        });

                        if (spawnedCommands.length > 0) {
                            // add commands to Bulk Run action menu at the top of the scan table
                            var menuTarget = $('#scanActionsMenu');
                            launcher.createMenu(menuTarget,spawnedCommands);
                            $('.scan-actions-controls').show();
                            $('#scanTable-run-containers').removeClass('hidden');
                        }
                    }
                },
                fail: function(e) {
                    errorHandler(e);
                }
            });
        }

    };

    launcher.open = window.openCommandLauncher = function(obj){
        var launcher = obj.launcher,
            wrapperId = obj.wrapperid,
            rootElement = obj.rootElement,
            rootElementValue = obj.rootElementValue || undefined;

        switch(launcher) {
            case 'select-scan':
                XNAT.plugin.containerService.launcher.bulkSelectDialog(wrapperId,'scan');
                break;
            case 'single-scan':
                var rootElementPath = obj.uri;
                XNAT.plugin.containerService.launcher.singleScanDialog(wrapperId,rootElementPath);
                break;
            case 'multiple-scans':
                var listOfScanIds = [];
                $('.selectable').find('tbody').find('input:checked').each(function(){
                    var scanId = $(this).val();
                    var scanPath = fullScanPath(scanId);
                    listOfScanIds.push(scanPath);
                });
                XNAT.plugin.containerService.launcher.bulkLaunchDialog(wrapperId,'scan',listOfScanIds);
                break;
            default:
                XNAT.plugin.containerService.launcher.defaultLauncher(wrapperId,rootElement,rootElementValue);
        }
    };

    $(document).on('click','.commandLauncher',function(){
        var launcherObj = $(this).data();
        launcher.open(launcherObj);
    });

    $(document).on('click','.advanced-settings-toggle',function(){
        var advancedPanel = $(this).parent('.advanced-settings-container').find('.advanced-settings');
        if ($(this).hasClass('active')) {
            $(this).removeClass('active');
            advancedPanel.slideUp(300);
        } else {
            $(this).addClass('active');
            advancedPanel.slideDown(300);
        }
    });

    launcher.refresh = function(){
        launcherMenu.html('');
        launcher.init();
    };

    $(document).ready(function(){
        launcher.init();
    });

}));