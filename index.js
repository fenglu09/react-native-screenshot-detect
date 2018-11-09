import {
    NativeEventEmitter,
    NativeModules,
    DeviceEventEmitter,
    Platform,
    AppState
} from 'react-native';
const isIOS = Platform.OS == 'ios';
const ScreenShotDetect = NativeModules.RNScreenShotDetect;
const screenshotDetectEmitter = new NativeEventEmitter(ScreenShotDetect);
const subscription = null;
const isListening = false;
function addIOSEventListener(handler) {
    // 开始监听，初始化
    removeAllListener();
    ScreenShotDetect.startListener();
    subscription = screenshotDetectEmitter.addListener('ScreenShotDetected', (result) => {
        result.timeStamp = result.timeStamp * 1000;
        handler(result);
    })
    return subscription;
}

function removeAllListener() {
    if (subscription) {
        try {
            subscription.remove();
            subscription = null;
        } catch (error) {
        
        }
    }
}
function startListen() {
    if (!isIOS && !isListening) {
        isListening = true;
        ScreenShotDetect.startListen();
    }
}
function stopListen() {
    if (!isIOS && isListening) {
        isListening = false;
        ScreenShotDetect.stopListen();
    }
}
function addAndroidEventListener(handler) {
    removeAllListener();
    startListen();
    subscription = DeviceEventEmitter.addListener('ScreenShotDetected', (result) => {
        handler(result);
    });
   
    return subscription;
}
export default {
    addEventListener: function (handler) {
        if (isIOS) {
            return addIOSEventListener(handler);
        } else {
            return addAndroidEventListener(handler)
        }
    },
    startListener: startListen,
    stopListener: stopListen,
    clearListener: removeAllListener,
}

