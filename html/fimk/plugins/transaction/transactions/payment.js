// send money
(function () {
'use strict';
var module = angular.module('fim.base');
module.run(function (plugins, modals, $q, $rootScope, nxt) {
  
  var plugin = plugins.get('transaction');

  plugin.add({
    label: 'Send Money',
    id: 'sendMoney',
    execute: function (senderRS, args) {
      args = args||{};
      return plugin.create(angular.extend(args, {
        title: 'Send Money',
        message: 'Sends money to recipient',
        senderRS: senderRS,
        requestType: 'sendMoney',
        canHaveRecipient: true,
        editRecipient: true,
        createArguments: function (items) {
          var _args = {
            recipient: nxt.util.convertRSAddress(items.recipient),
            amountNQT: nxt.util.convertToNQT(items.amountNXT)
          }
          if (items.recipientPublicKey) {
            _args.recipientPublicKey = items.recipientPublicKey;
          }
          return _args;
        },
        fields: [/*{
          label: 'Recipient',
          name: 'recipient',
          type: 'text',
          value: args.recipient||'',
          validate: function (text) { 
            this.errorMsg = null;
            if (plugin.validators.address(text) === false) { this.errorMsg = 'Invalid address'; }
            return ! this.errorMsg;
          },
          required: true
        },*/ {
          label: 'Recipient public key',
          name: 'recipientPublicKey',
          type: 'text',
          value: args.recipientPublicKey||'',
          required: false,
          show: 'show.showPublicKey'          
        }, {
          label: 'Amount',
          name: 'amountNXT',
          type: 'money',
          value: args.amountNXT||'',
          required: true
        }]
      }));
    }
  });

  plugin.add({
    label: 'Tip User',
    id: 'tipUser',
    exclude: true,
    execute: function (args) {
      args = args||{};
      return plugin.create(angular.extend(args, {
        title: 'Send Money',
        message: 'Sends money to recipient',
        editSender: true,
        editRecipient: false,
        recipient: args.recipient||'',
        requestType: 'sendMoney',
        canHaveRecipient: true,
        createArguments: function (items) {
          var _args = {
            recipient: nxt.util.convertRSAddress(items.recipient),
            amountNQT: nxt.util.convertToNQT(items.amountNXT)
          }
          if (items.recipientPublicKey) {
            _args.recipientPublicKey = items.recipientPublicKey;
          }
          return _args;
        },
        fields: [/*{
          label: 'Recipient',
          name: 'recipient',
          type: 'text',
          value: args.recipient||'',
          readonly: true
        }, */{
          label: 'Recipient public key',
          name: 'recipientPublicKey',
          type: 'text',
          value: args.recipientPublicKey||'',
          required: false,
          show: 'show.showPublicKey'
        }, {
          label: 'Amount',
          name: 'amountNXT',
          type: 'money',
          value: args.amountNXT||'',
          required: true
        }]
      }));
    }
  });


});
})();