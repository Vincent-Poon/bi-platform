define(["report/global-menu-btns/component-menu-template"],function(a){return Backbone.View.extend({initialize:function(){},componentMenu:function(){return a.render()},shiftMenu:function(a){var b=$(a.target).parent().attr("id"),c=$(".global-menus").not("#"+b);c.hide();var d=$(".comp-menu").find("#"+b);"none"==d.css("display")?d.show():d.hide()}})});