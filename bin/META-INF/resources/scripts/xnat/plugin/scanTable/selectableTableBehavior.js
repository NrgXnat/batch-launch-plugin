/* selectable table checkbox behavior */
$(document).on('click','.selectable-select-all',function(){
    var checkProp = false;
    var containingTable = $(this).parents('.data-table-container');

    // check all checkboxes in the table body to find any unchecked. Default behavior will be to check all.
    containingTable.find('tbody').find('.selectable-select-one').each(function(){
        if (!$(this).is(':checked')) {
            checkProp = 'checked';
            return false;
        }
    });

    // now iterate over all checkboxes again, performing the default operation
    containingTable.find('tbody').find('.selectable-select-one').each(function(){
        $(this).prop('checked',checkProp);
    });

    if (checkProp === 'checked') {
        containingTable.find('.data-table-action').removeClass('disabled');
    } else {
        containingTable.find('.data-table-action').addClass('disabled');
    }
});

// set toggle-all selector state based on internal checkbox statuses
$(document).on('click','.selectable-select-one',function(){
    var allChecked = true,
        allUnchecked = true,
        containingTable = $(this).parents('.data-table-container'),
        selectAll = containingTable.find('.selectable-select-all');

    // check for unchecked boxes. If any found, allChecked = false
    containingTable.find('tbody').find('.selectable-select-one').each(function(){
        if (!$(this).is(':checked')) {
            allChecked = false;
            return false;
        }
    });

    // check for checked boxes. If any found, unchecked = false
    containingTable.find('tbody').find('.selectable-select-one').each(function(){
        if ($(this).is(':checked')) {
            allUnchecked = false;
            return false;
        }
    });

    var indeterminate = !(allChecked || allUnchecked); // indeterminate is only true if all checkboxes are neither checked or unchecked

    selectAll.prop('checked',allChecked).prop('indeterminate',indeterminate);

    if (allUnchecked) {
        containingTable.find('.data-table-action').addClass('disabled');
    } else {
        containingTable.find('.data-table-action').removeClass('disabled');
    }
});

// use the inline menu toggle as the trigger for opening the menu.
// hide the menu after a short time if the user does not mouse into the menu.
$(document).on('mouseenter','.inline-actions-menu-toggle',function(){
    var menu = $(this).parents('td').find('.inline-actions-menu');
    menu.show();

    setTimeout(function(){
        if (!menu.hasClass('active')) menu.fadeOut(150);
    },1500);
});

$(document).on('mouseenter','.inline-actions-menu',function(){ $(this).addClass('active'); });
$(document).on('mouseleave','.inline-actions-menu',function(){ $(this).removeClass('active').hide(); });

// if user mouses away from the toggle button, hide the menu unless the user has moused over the menu
$(document).on('mouseleave','.inline-actions-menu-toggle',function(event){
    var menu = $(this).parents('td').find('.inline-actions-menu');
    if (!event.relatedTarget.className.match(/menu/) ) {
        setTimeout(function(){
            if (!menu.hasClass('active')) menu.hide();
        },100);
    }
});