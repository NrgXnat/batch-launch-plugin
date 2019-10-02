/*
 * Copyright 2019 Radiologics, Inc
 */

$(document).ready(function() {
    var style_string = "color: white;\n" +
        "    font-size: 26px;\n" +
        "    position: absolute;\n" +
        "    top: 8px;\n" +
        "    right: 279px;";
    var $dashboard = $(
        "<div>" +
        "   <a href='/app/template/UserDashboard.vm' title='User dashboard' id='user-dashboard' style='" + style_string + "'" +
        "       <i class='fa fa-list-alt'></i>" +
        "   </a>" +
        "</div>"
    );
    $("#main_nav div.inner").append($dashboard);
});