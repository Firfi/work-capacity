AJS.$.namespace("GB.gadget.fields");
GB.gadget.fields.statusPicker = function(gadget, userpref, options, label) {
    if(!AJS.$.isArray(options)){
        options = [options];
    }

      return {
        id: "statuspicker_" + userpref,
        userpref: userpref,
        label: label||"Status Picker",
        description: "Select Status",
        type: "multiselect",
        selected: gadget.getPref(userpref),
        options: options
      };
};
GB.gadget.fields.userPicker = function(gadget, userpref, options, label) {
      if(!AJS.$.isArray(options)){
          options = [options];
      }
    return {
      id: "userpicker_" + userpref,
      userpref: userpref,
      label: label||"User Picker",
      description: "Select Users",
      type: "multiselect",
      selected: gadget.getPref(userpref),
      options: options
    };

};

