//
//  RNScreenShotDetect.h
//  RNScreenShotDetect
//
//  Created by Jason on 2017/11/14.
//  Copyright © 2017年 Jason. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RNScreenShotDetect : RCTEventEmitter <RCTBridgeModule>

@end

RNScreenShotDetect *sharedRNScreenShotDetect;
Boolean isApplicationActive;
