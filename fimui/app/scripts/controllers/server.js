(function () {
'use strict';
var module = angular.module('fim.base');

module.controller('serverController', function ($scope, serverService, $timeout) {
  $scope.serverRunning  = false;
  $scope.messages       = [];
  $scope.serverService  = serverService;
  $scope.bufferSize     = 100;
  $scope.isNodeJS       = true // serverService.isNodeJS();

  serverService.addListener('exit', function () {
    $timeout(function () { $scope.serverRunning = false });
    console.log('$scope.serverRunning = false;')
  });  

  serverService.addListener('start', function () {
    $timeout(function () { $scope.serverRunning = true });
    console.log('$scope.serverRunning = true;')
  });  

  function updateConsole() {
    var index = (serverService.messages.length > $scope.bufferSize) ? 0 - $scope.bufferSize : 0;
    $timeout(function () { $scope.messages = serverService.messages.slice(index) });
  }

  serverService.addListener('stdout', updateConsole);  
  serverService.addListener('stderr', updateConsole);    
});

})();