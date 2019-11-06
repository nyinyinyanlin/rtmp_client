var exec = require('cordova/exec');
var rtmp_client = {};

rtmp_client.streamRTMP = function(uri, success, failure) {
    // fire
    exec(
        success,
        failure,
        'VideoStream',
        'streamRTMP',
        [uri]
    );
};

rtmp_client.streamStop = function(success, failure) {
    // fire
    exec(
        success,
        failure,
        'VideoStream',
        'streamStop',
        []
    );
};

module.exports = rtmp_client;