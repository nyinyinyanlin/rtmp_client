var exec = require('cordova/exec');
var rtmp_client = {};

rtmp_client.streamRTMP = function(uri, token, success, failure) {
    // fire
    exec(
        success,
        failure,
        'RTMP-Client',
        'streamRTMP',
        [uri,token]
    );
};

rtmp_client.streamStop = function(success, failure) {
    // fire
    exec(
        success,
        failure,
        'RTMP-Client',
        'streamStop',
        []
    );
};

module.exports = rtmp_client;