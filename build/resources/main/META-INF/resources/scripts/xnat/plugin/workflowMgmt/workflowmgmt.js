// Workflowmgmt File
	function killContainer(containerId){
	      XNAT.ui.dialog.open({
			title: 'Kill container confirmation',
			content: 'Are you sure you want to kill the container?',
			buttons: [
			    {
				label: 'OK',
				isDefault: true,
				close: true,
				action: function(obj){
					    xmodal.loading.open('#wait');
					    function killContainerOK(data, status, o){
						xmodal.loading.close();
						xmodal.message('Success','Successfully terminated container');
					    }

					    function killContainerFailed(o, status, error){
						xmodal.loading.close();
						xmodal.message('Error','An unexpected error has occurred while killing container ' + containerId + '. Please contact your administrator.');
					    }
					    XNAT.xhr.post({
						    url: XNAT.url.rootUrl('/xapi/containers/'+containerId+'/kill'),
						    success: killContainerOK,
						    error : killContainerFailed
					   });
					   XNAT.ui.dialog.closeAll();
					}
			    },
			    {
	                        label: 'Cancel',
	                        isDefault: false,
	                        close: true
	                    }
			]
              });
	};

	function killProcess(workFlowId){
	      XNAT.ui.dialog.open({
			title: 'Terminate process confirmation',
			content: 'Are you sure you want to terminate the process?',
			buttons: [
			    {
				label: 'OK',
				isDefault: true,
				close: true,
				action: function(obj){
					    xmodal.loading.open('#wait');
					    function killContainerOK(data, status, o){
						xmodal.loading.close();
						xmodal.message('Success','Successfully terminated process');
					    }

					    function killContainerFailed(o, status, error){
						xmodal.loading.close();
						xmodal.message('Error','An unexpected error has occurred while killing process ' + workFlowId + '. Please contact your administrator.');
					    }
					    XNAT.xhr.post({
						    url: XNAT.url.rootUrl('/xapi/workflows/'+workFlowId+'/kill'),
						    success: killContainerOK,
						    error : killContainerFailed
					   });
					   XNAT.ui.dialog.closeAll();
					}
			    },
			    {
	                        label: 'Cancel',
	                        isDefault: false,
	                        close: true
	                    }
			]
              });
	};
