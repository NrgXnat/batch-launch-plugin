$(document).ready(function() {
    var style_string = "color: white; font-size: 36px; position: absolute; top: 3px; right: 283px;";
    var $dashboard = $(
        "<div>" +
        "   <a href='/app/template/UserDashboard.vm' title='User dashboard' id='user-dashboard' style='" + style_string + "'" +
        "       <i class='fa fa-list-alt'></i>" +
        "   </a>" +
        "</div>"
    );
    $("#main_nav div.inner").append($dashboard);
});