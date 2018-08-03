<%@ page session="true" contentType="text/html" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="pg" tagdir="/WEB-INF/tags/page" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<pg:init/>
<pg:jsvars/>
<c:set var="SITE_ROOT" value="${sessionScope.siteRoot}"/>

<c:if test="${not empty param.id}">
    <c:set var="id">${fn:escapeXml(param.id)}</c:set>
</c:if>


<div id="page-wrapper">
    <div class="pad">


        <div id="project-not-specified" class="error hidden">Project not specified.</div>

        <%-- show an error if the project data is not returned from the rest call --%>
        <div id="project-data-error" class="error hidden">Data for "<span class="project-id"></span>" project not found.</div>

        <%-- if an 'id' param is passed, use its value to edit specified project data --%>
        <h3 id="project-settings-header" class="hidden">Processing History for <span class="project-id"></span></h3>

	
          <div id="batch-history-container" class="panel panel-default">
                 <div class="panel-body">
                </div>
          </div>      

        <script type="text/javascript">
        	var PROJECT_ID = '${id}' || getQueryStringValue('id') || getUrlHashValue('#id=');
        	$('span.project-id').text(PROJECT_ID);
        	alert(PROJECT_ID);
        </script>

	<script type="text/javascript" src="${pageContext.request.contextPath}/scripts/xnat/plugin/batchLaunch/commandHistory.js"></script> 


        <script type="text/javascript">
        	var procHistory = Object.create(ProcessingHistory);
        	procHistory.project = PROJECT_ID;
        	var displayContainer = $('#batch-history-container').find('div.panel').find('div.panel-body');

        	procHistory.showTable(displayContainer);
        	alert("AGAIN" + PROJECT_ID);
        </script>
	
        


    </div>
</div>

