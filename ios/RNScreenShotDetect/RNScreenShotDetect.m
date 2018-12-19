//
//  RNScreenShotDetect.m
//  RNScreenShotDetect
//
//  Created by Jason on 2017/11/14.
//  Copyright © 2017年 Jason. All rights reserved.
//

#import "RNScreenShotDetect.h"

#import <React/RCTUtils.h>
#import <React/RCTBridge.h>
#import <React/RCTEventDispatcher.h>


static NSString*  const ScreenShotDetectedEvent = @"ScreenShotDetected";
@implementation RNScreenShotDetect

RCT_EXPORT_MODULE(RNScreenShotDetect);

- (NSArray<NSString *> *)supportedEvents
{
    return @[ScreenShotDetectedEvent];
}

RCT_EXPORT_METHOD(startListener)
{
    if (!sharedRNScreenShotDetect) {
        sharedRNScreenShotDetect = self;
    } else {
        // 所已经初始化过后，就不需要再次注册监听事件了，以免重复
        return;
    }
    isApplicationActive = TRUE;
    if ([[[UIDevice currentDevice] systemVersion] floatValue] >= 0.7) {
        
        // 监听截图通知
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(dealWidthScreenShot:) name: UIApplicationUserDidTakeScreenshotNotification object:nil];
        
        // 添加检测app进入后台的观察者：包括在app页面，下拉通知，以及进入到后台
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(dealWidthEnterBackground:) name: UIApplicationWillResignActiveNotification object:nil];
        
        // 添加进入到前台的通知
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(dealWidthActive:) name: UIApplicationDidBecomeActiveNotification object:nil];
        
    }
}
// 截屏后的处理函数
-(void) dealWidthScreenShot: (NSNotification *) notification
{
    if (isApplicationActive) {
//        NSData *imageData = [self imageWithScreenshot];
        NSData *imageData = [sharedRNScreenShotDetect imageWithScreenshot];
        NSString *base64Encode = [imageData base64EncodedStringWithOptions: 0];
        NSDate *date = [NSDate date];
        
        NSDictionary *jsonObj = @{
                                  @"URI": [NSString stringWithFormat:@"data:image/jpeg;base64,%@", base64Encode],
                                  @"timeStamp": [NSString stringWithFormat:@"%ld", (long) [date timeIntervalSince1970]]
                                  };
        [sharedRNScreenShotDetect sendEventWithName:ScreenShotDetectedEvent body:jsonObj];
    }
}

// app进入到后台的处理函数
- (void)dealWidthEnterBackground:(NSNotification *) notification
{
    //进入后台
    NSLog(@"进入后台");
    isApplicationActive = FALSE;
}
// app进入到前台后的处理函数
-(void) dealWidthActive: (NSNotification*) notification {
    //进入前台
    NSLog(@"进入前台");
    isApplicationActive = TRUE;
}

// 获取截屏图片信息
-(NSData *)imageWithScreenshot {
    NSData *data = [self dataWithScreenshotInPNGFormat];
    //    NSLog("image: ", str);
    return data;
}

- (NSData *)dataWithScreenshotInPNGFormat  {
    
    CGSize imageSize = CGSizeZero;
    // 获取当前app的方向。
    UIInterfaceOrientation orientation = [UIApplication sharedApplication].statusBarOrientation;
    // 竖屏
    if (UIInterfaceOrientationIsPortrait(orientation))
        imageSize = [UIScreen mainScreen].bounds.size;
    else
        imageSize = CGSizeMake([UIScreen mainScreen].bounds.size.height, [UIScreen mainScreen].bounds.size.width);
    
    UIGraphicsBeginImageContextWithOptions(imageSize, NO, 0);
    CGContextRef context = UIGraphicsGetCurrentContext();
    for (UIWindow *window in [[UIApplication sharedApplication] windows])
    {
        CGContextSaveGState(context);
        CGContextTranslateCTM(context, window.center.x, window.center.y);
        CGContextConcatCTM(context, window.transform);
        CGContextTranslateCTM(context, -window.bounds.size.width * window.layer.anchorPoint.x, -window.bounds.size.height * window.layer.anchorPoint.y);
        if (orientation == UIInterfaceOrientationLandscapeLeft)
        {
            CGContextRotateCTM(context, M_PI_2);
            CGContextTranslateCTM(context, 0, -imageSize.width);
        }
        else if (orientation == UIInterfaceOrientationLandscapeRight)
        {
            CGContextRotateCTM(context, -M_PI_2);
            CGContextTranslateCTM(context, -imageSize.height, 0);
        } else if (orientation == UIInterfaceOrientationPortraitUpsideDown) {
            CGContextRotateCTM(context, M_PI);
            CGContextTranslateCTM(context, -imageSize.width, -imageSize.height);
        }
        if ([window respondsToSelector:@selector(drawViewHierarchyInRect:afterScreenUpdates:)])
        {
            [window drawViewHierarchyInRect:window.bounds afterScreenUpdates:YES];
        }
        else
        {
            [window.layer renderInContext:context];
        }
        CGContextRestoreGState(context);
    }
    
    UIImage *image = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    return UIImagePNGRepresentation(image);
}
@end
