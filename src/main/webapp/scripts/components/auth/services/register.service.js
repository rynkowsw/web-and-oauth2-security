'use strict';

angular.module('testApp')
    .factory('Register', function ($resource) {
        return $resource('api/register', {}, {
        });
    });


