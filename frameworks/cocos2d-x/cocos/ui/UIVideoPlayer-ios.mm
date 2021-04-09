/****************************************************************************
 Copyright (c) 2014-2016 Chukong Technologies Inc.
 Copyright (c) 2017-2018 Xiamen Yaji Software Co., Ltd.

 http://www.cocos2d-x.org

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ****************************************************************************/

#include "ui/UIVideoPlayer.h"

// No Available on tvOS
#if CC_TARGET_PLATFORM == CC_PLATFORM_IOS && !defined(CC_TARGET_OS_TVOS)

using namespace cocos2d::experimental::ui;
//-------------------------------------------------------------------------------------

#include "platform/ios/CCEAGLView-ios.h"
#import <MediaPlayer/MediaPlayer.h>
#include "base/CCDirector.h"
#include "platform/CCFileUtils.h"
#import <CCApplication.h>
#import <AVFoundation/AVFoundation.h>
#include <AVKit/AVPlayerViewController.h>
#import <CoreMedia/CoreMedia.h>
typedef NS_ENUM(NSUInteger, VideoPlayerStatus) {
    VideoPlayerStatusUnknow,
    VideoPlayerStatusLoading,
    VideoPlayerStatusPlaying,
    VideoPlayerStatusPaused,
    VideoPlayerStatusEnd,
    VideoPlayerStatusError
};
@interface UIVideoViewWrapperIos : NSObject
@property (strong,nonatomic)  NSURL * videoURL;
@property (nonatomic, assign) VideoPlayerStatus  status;
@property (strong,nonatomic) AVPlayerViewController * moviePlayer;
@property (nonatomic, assign) NSTimeInterval    seekTime;
@property (nonatomic, assign) BOOL  seeking;
@property (nonatomic, assign) BOOL needToResume;

- (void) setFrame:(int) left :(int) top :(int) width :(int) height;
- (void) setURL:(int) videoSource :(std::string&) videoUrl;
- (void) play;
- (void) pause;
- (void) resume;
- (void) stop;
- (void) seekTo:(float) sec;
- (void) setVisible:(BOOL) visible;
- (void) setKeepRatioEnabled:(BOOL) enabled;
- (void) setFullScreenEnabled:(BOOL) enabled;
//获取时长
- (int) getVideoDuration;
- (int) getVideoCurrentTime;
- (void) setVolume:(float)value;
- (void) setBrightness:(float)value;
- (BOOL) isFullScreenEnabled;

-(id) init:(void*) videoPlayer;

-(void) videoFinished:(NSNotification*) notification;

@end

@implementation UIVideoViewWrapperIos
{
    int _left;
    int _top;
    int _width;
    int _height;
    CMTime _currentTime;
    bool _paused;
    NSURL* _url;

    VideoPlayer* _videoPlayer;
}

-(id)init:(void*)videoPlayer
{
    if (self = [super init]) {
        self.moviePlayer = nullptr;
        _moviePlayer = nullptr;
        _videoPlayer = (VideoPlayer*)videoPlayer;
        _paused = false;
        _url = nullptr;
        _seeking = NO;
        _needToResume = NO;
    }

    return self;
}

-(void) dealloc
{
    [self destroyMoviePlayer];
    [self destroyURL];

    [super dealloc];
}

-(void) setFrame:(int)left :(int)top :(int)width :(int)height
{
    _left = left;
    _width = width;
    _top = top;
    _height = height;
    if (self.moviePlayer != nullptr) {
        [self.moviePlayer.view setFrame:CGRectMake(left, top, width, height)];
    }
}

-(void) setFullScreenEnabled:(BOOL) enabled
{

}

-(BOOL) isFullScreenEnabled
{
    return false;
}

- (int) getVideoDuration
{
    if (AVPlayerStatusReadyToPlay != self.moviePlayer.player.status) {
        return CGFLOAT_MIN;
    }
    Float64 result = CMTimeGetSeconds(self.moviePlayer.player.currentItem.duration);
    return isnan(result) ? CGFLOAT_MIN : result;
}

- (int) getVideoCurrentTime
{
    if (self.seeking) {
        return self.seekTime;
    }
    
    if (AVPlayerStatusReadyToPlay !=  self.moviePlayer.player.status) {
        return CGFLOAT_MIN;
    }
    Float64 result = CMTimeGetSeconds(self.moviePlayer.player.currentItem.currentTime);
    return isnan(result) ? CGFLOAT_MIN : result;
}

-(void) setURL:(int)videoSource :(std::string &)videoUrl
{
    [self destroyMoviePlayer];
    
    // should initialize url first
    [self initializeURL:videoSource url:videoUrl];
    [self initializeMoviePlayer];
}

-(void) videoFinished:(NSNotification *)notification
{
    if(_videoPlayer != nullptr)
    {
        self.status = VideoPlayerStatusEnd;
        _videoPlayer->onPlayEvent((int)VideoPlayer::EventType::COMPLETED);

        [self destroyMoviePlayer];
    }
}

#pragma mark - KVO

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary<NSKeyValueChangeKey,id> *)change context:(void *)context {
    if (object == self.moviePlayer.player.currentItem) {
        if ([keyPath isEqualToString:@"status"]) {
            switch (self.moviePlayer.player.currentItem.status) {
                case AVPlayerItemStatusReadyToPlay:
                   {
                       NSLog(@"AVPlayer: ready to play");
                       if (self.status != VideoPlayerStatusPaused) {
                           self.status = VideoPlayerStatusPlaying;
                           _videoPlayer->onPlayEvent((int)VideoPlayer::EventType::PLAYING);
                       }
                   }
                    break;
                case AVPlayerItemStatusFailed:
                  {
                      NSLog(@"AVPlayer: failed");
                      self.status = VideoPlayerStatusError;
                      _videoPlayer->onPlayEvent((int)VideoPlayer::EventType::PLAYERROR);
                    
                  }
                    break;
                case AVPlayerItemStatusUnknown:
                default:
                    break;
            }
        } else if ([keyPath isEqualToString:@"isPlaybackBufferEmpty"]) {
            NSLog(@"AVPlayer: buffer empty");
            if (self.moviePlayer.player.currentItem.playbackBufferEmpty) {
                self.status = VideoPlayerStatusLoading;
                _videoPlayer->onPlayEvent((int)VideoPlayer::EventType::PLAYSTALLED);
            }
        } else if ([keyPath isEqualToString:@"isPlaybackLikelyToKeepUp"]) {
            NSLog(@"AVPlayer: likely to keep up");
            if (VideoPlayerStatusLoading == self.status
                && self.moviePlayer.player.currentItem.playbackLikelyToKeepUp) {
                self.status = VideoPlayerStatusPlaying;
                _videoPlayer->onPlayEvent((int)VideoPlayer::EventType::PLAYING);
            }
        }
    }
}

-(void) seekTo:(float)sec
{
    if (self.moviePlayer != NULL) {
        
        if (AVPlayerStatusReadyToPlay != self.moviePlayer.player.status) {
            return;
        }
        
        CMTime newTime = self.moviePlayer.player.currentItem.currentTime;
        newTime.value = sec * newTime.timescale;
        
        self.seeking = YES;
        self.seekTime = sec;
        
        __block __typeof(self) weakSelf = self;
        [self.moviePlayer.player.currentItem cancelPendingSeeks];
        [self.moviePlayer.player.currentItem seekToTime:newTime completionHandler:^(BOOL finished) {
            if (weakSelf) {
                weakSelf.seeking = NO;
            }
        }];
    }
}

-(void) setVisible:(BOOL)visible
{
    if (self.moviePlayer != NULL) {
        [self.moviePlayer.view setHidden:!visible];
    }
}

-(void) setKeepRatioEnabled:(BOOL)enabled
{
    
}

-(void) play
{
    if (self.isPlaying) {
        [self.moviePlayer.player play];
        return;
    }
    
    if (!self.moviePlayer.player.currentItem.isPlaybackLikelyToKeepUp) {
        self.status = VideoPlayerStatusLoading;
        _videoPlayer->onPlayEvent((int)VideoPlayer::EventType::PLAYSTALLED);
    }
    
    self.needToResume = YES;

    if (self.moviePlayer == NULL) {
        [self initializeMoviePlayer];
    }
    
    if (self.moviePlayer != NULL) {
        
        if (self.moviePlayer.player.currentItem.isPlaybackLikelyToKeepUp) {
            self.status = VideoPlayerStatusPlaying;
            _videoPlayer->onPlayEvent((int)VideoPlayer::EventType::PLAYING);
        }
        
        [self.moviePlayer.view setFrame:CGRectMake(_left, _top, _width, _height)];
        [self.moviePlayer.player play];
        _paused = false;
        _currentTime = CMTimeMake(0, 100);
    }
}

-(void) pause
{
    if (self.moviePlayer != NULL) {
         self.status = VideoPlayerStatusPaused;
        _videoPlayer->onPlayEvent((int)VideoPlayer::EventType::PAUSED);

        _currentTime = self.moviePlayer.player.currentTime;
        _paused = true;
        [self.moviePlayer.player pause];
        self.needToResume = NO;
    }
}

-(void) resume
{
    if (self.moviePlayer != nullptr && _paused)
    {
        [self seekTo: CMTimeGetSeconds(_currentTime)];
        [self play];
        _paused = false;
    }
}

-(void) stop
{
    [self destroyMoviePlayer];
    _paused = false;
}

-(void) destroyMoviePlayer
{
    _status = VideoPlayerStatusUnknow;
    if (self.moviePlayer != nullptr)
    {
        [[NSNotificationCenter defaultCenter] removeObserver:self name:AVPlayerItemDidPlayToEndTimeNotification object:self.moviePlayer.player.currentItem];
         [[NSNotificationCenter defaultCenter] removeObserver:self name:AVAudioSessionInterruptionNotification object:nil];
         [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationWillEnterForegroundNotification object:nil];
      
        [[NSNotificationCenter defaultCenter] removeObserver:self name:AVPlayerItemPlaybackStalledNotification object:nil];

        [self.moviePlayer.player.currentItem removeObserver:self forKeyPath:@"status"];
        [self.moviePlayer.player.currentItem removeObserver:self forKeyPath:@"isPlaybackBufferEmpty"];
        [self.moviePlayer.player.currentItem removeObserver:self forKeyPath:@"isPlaybackLikelyToKeepUp"];
        
        // It is more reasonable to invoke `stop` here, but because `stop` will invoke `destroyMoviePlayer` to real stop the video
        // so inoke `pause` here.
        [self pause];
        self.needToResume = NO;
        
        [self.moviePlayer.player.currentItem cancelPendingSeeks];
        [self.moviePlayer.player replaceCurrentItemWithPlayerItem:nil];
        [self.moviePlayer.view removeFromSuperview];
        self.moviePlayer = nullptr;
    }
}


-(void) initializeMoviePlayer
{
    [self destroyMoviePlayer];
    
    self.moviePlayer = [[AVPlayerViewController alloc] init];
    self.moviePlayer.view.userInteractionEnabled = true;
    self.moviePlayer.showsPlaybackControls = false;
    self.moviePlayer.player = [AVPlayer playerWithURL: _url];
    
    auto clearColor = [UIColor clearColor];
    self.moviePlayer.view.backgroundColor = clearColor;
    for (UIView * subView in self.moviePlayer.view.subviews)
        subView.backgroundColor = clearColor;
    
    auto view = cocos2d::Director::getInstance()->getOpenGLView();
    auto eaglview = (CCEAGLView *) view->getEAGLView();
     [[eaglview.superview viewWithTag:1] addSubview:self.moviePlayer.view];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(videoFinished:) name:AVPlayerItemDidPlayToEndTimeNotification object:self.moviePlayer.player.currentItem];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleAudioSessionInterruption:)
                                                    name:AVAudioSessionInterruptionNotification
                                                     object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self
                                                   selector:@selector(handleEnterForeground:)
                                                       name:UIApplicationWillEnterForegroundNotification
                                                     object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self
                                                    selector:@selector(handlePlaybackStall:)
                                                        name:AVPlayerItemPlaybackStalledNotification
                                                      object:nil];

    [self.moviePlayer.player.currentItem addObserver:self forKeyPath:@"status" options:NSKeyValueObservingOptionNew context:nil];
    [self.moviePlayer.player.currentItem addObserver:self forKeyPath:@"isPlaybackBufferEmpty" options:NSKeyValueObservingOptionNew context:nil];
    [self.moviePlayer.player.currentItem addObserver:self forKeyPath:@"isPlaybackLikelyToKeepUp" options:NSKeyValueObservingOptionNew context:nil];
}

- (void)pauseToResume:(BOOL)needToResume {
    self.needToResume = needToResume;
    self.status = VideoPlayerStatusPaused;
    [self.moviePlayer.player pause];
}

- (void)handlePlaybackStall:(NSNotification *)notification {

}

- (void)handleAudioSessionInterruption:(NSNotification*)notification {
    
    NSNumber *interruptionType = [[notification userInfo] objectForKey:AVAudioSessionInterruptionTypeKey];
    NSNumber *interruptionOption = [[notification userInfo] objectForKey:AVAudioSessionInterruptionOptionKey];
    
    if (!self.needToResume) {
        return ;
    }
    
    switch (interruptionType.unsignedIntegerValue) {
        case AVAudioSessionInterruptionTypeBegan:{
            // • Audio has stopped, already inactive
            // • Change state of UI, etc., to reflect non-playing state
            if (self.isPlaying) {
                [self pauseToResume:YES];
            }
        } break;
        case AVAudioSessionInterruptionTypeEnded:{
            // • Make session active
            // • Update user interface
            // • AVAudioSessionInterruptionOptionShouldResume option
            if (interruptionOption.unsignedIntegerValue == AVAudioSessionInterruptionOptionShouldResume) {
                // Here you should continue playback.
                if (!self.isPlaying) {
                    [self play];
                }
            }
        } break;
        default:
            break;
    }
}

- (void)handleEnterForeground:(NSNotification *)notif {
    if (self.isPlaying && self.needToResume) {
        [self play];
    }
}

- (BOOL)isPlaying {
    return VideoPlayerStatusPlaying == self.status || VideoPlayerStatusLoading == self.status;
}

-(void) initializeURL:(int)urlType url:(std::string&)url
{
    [self destroyURL];
    
    if (urlType == 0)
        _url = [[NSURL alloc] initFileURLWithPath:@(url.c_str())];
    else
        _url = [[NSURL alloc] initWithString:@(url.c_str())];
}

-(void) destroyURL
{
    _url = nullptr;
}

static float plus = -100;

static float minus = -100;
-(void) setVolume:(float)value{
    
    int radio = 2;
    MPMusicPlayerController * mp = [MPMusicPlayerController applicationMusicPlayer];

    if (value < 0) {
        plus = -100;
        if (minus == -100) {
            minus = mp.volume;
        }
        if (minus != -100 && minus > mp.volume &&mp.volume !=0 &&minus !=1 ) {
            minus = mp.volume;

            return;
        }
        minus = mp.volume;

        mp.volume = MAX(0, mp.volume +  value * radio);
    }else{
        minus = -100;
        if (plus == -100) {
            plus = mp.volume;
        }
        if (plus != -100 && plus < mp.volume && mp.volume != 1 && plus != 0) {
            plus = mp.volume;

            return;
        }
        plus = mp.volume;

        mp.volume = MIN(1, mp.volume + value * radio);
    }

}

-(void) setBrightness:(float)value{
    int radio = 2;

    if (value < 0) {
        [UIScreen mainScreen].brightness = MAX(0.,[UIScreen mainScreen].brightness + value*radio);
    }else{
         [UIScreen mainScreen].brightness = MIN(1, [UIScreen mainScreen].brightness + value*radio);
    }
    
    NSLog(@">>>>>setBrightness>>>%f>>>>>%f",[UIScreen mainScreen].brightness + value,[UIScreen mainScreen].brightness);
}

@end
//------------------------------------------------------------------------------------------------------------

VideoPlayer::VideoPlayer()
: _isPlaying(false)
, _fullScreenDirty(false)
, _fullScreenEnabled(false)
, _keepAspectRatioEnabled(false)
, _videoPlayerIndex(-1)
, _eventCallback(nullptr)
{
    _videoView = [[UIVideoViewWrapperIos alloc] init:this];

#if CC_VIDEOPLAYER_DEBUG_DRAW
    _debugDrawNode = DrawNode::create();
    addChild(_debugDrawNode);
#endif
}

VideoPlayer::~VideoPlayer()
{
    if(_videoView)
    {
        [((UIVideoViewWrapperIos*)_videoView) dealloc];
    }
}

void VideoPlayer::setFileName(const std::string& fileName)
{
    _videoURL = FileUtils::getInstance()->fullPathForFilename(fileName);
    _videoSource = VideoPlayer::Source::FILENAME;
    [((UIVideoViewWrapperIos*)_videoView) setURL:(int)_videoSource :_videoURL];
}

void VideoPlayer::setURL(const std::string& videoUrl)
{
    _videoURL = videoUrl;
    _videoSource = VideoPlayer::Source::URL;
    [((UIVideoViewWrapperIos*)_videoView) setURL:(int)_videoSource :_videoURL];
}

void VideoPlayer::draw(Renderer* renderer, const Mat4 &transform, uint32_t flags)
{
    cocos2d::ui::Widget::draw(renderer,transform,flags);

    if (flags & FLAGS_TRANSFORM_DIRTY)
    {
        auto directorInstance = Director::getInstance();
        auto glView = directorInstance->getOpenGLView();
        auto frameSize = glView->getFrameSize();
        auto scaleFactor = [static_cast<CCEAGLView *>(glView->getEAGLView()) contentScaleFactor];

        auto winSize = directorInstance->getWinSize();

        auto leftBottom = convertToWorldSpace(Vec2::ZERO);
        auto rightTop = convertToWorldSpace(Vec2(_contentSize.width,_contentSize.height));

        auto uiLeft = (frameSize.width / 2 + (leftBottom.x - winSize.width / 2 ) * glView->getScaleX()) / scaleFactor;
        auto uiTop = (frameSize.height /2 - (rightTop.y - winSize.height / 2) * glView->getScaleY()) / scaleFactor;

        [((UIVideoViewWrapperIos*)_videoView) setFrame :uiLeft :uiTop
                                                          :(rightTop.x - leftBottom.x) * glView->getScaleX() / scaleFactor
                                                          :( (rightTop.y - leftBottom.y) * glView->getScaleY()/scaleFactor)];
    }

#if CC_VIDEOPLAYER_DEBUG_DRAW
    _debugDrawNode->clear();
    auto size = getContentSize();
    Point vertices[4]=
    {
        Point::ZERO,
        Point(size.width, 0),
        Point(size.width, size.height),
        Point(0, size.height)
    };
    _debugDrawNode->drawPoly(vertices, 4, true, Color4F(1.0, 1.0, 1.0, 1.0));
#endif
}

bool VideoPlayer::isFullScreenEnabled()const
{
    return [((UIVideoViewWrapperIos*)_videoView) isFullScreenEnabled];
}

void VideoPlayer::setFullScreenEnabled(bool enabled)
{
    [((UIVideoViewWrapperIos*)_videoView) setFullScreenEnabled:enabled];
}

void VideoPlayer::setKeepAspectRatioEnabled(bool enable)
{
    if (_keepAspectRatioEnabled != enable)
    {
        _keepAspectRatioEnabled = enable;
        [((UIVideoViewWrapperIos*)_videoView) setKeepRatioEnabled:enable];
    }
}

void VideoPlayer::play()
{
    if (! _videoURL.empty())
    {
        [((UIVideoViewWrapperIos*)_videoView) play];
    }
}

void VideoPlayer::pause()
{
    if (! _videoURL.empty())
    {
        [((UIVideoViewWrapperIos*)_videoView) pause];
    }
}

void VideoPlayer::resume()
{
    if (! _videoURL.empty())
    {
        [((UIVideoViewWrapperIos*)_videoView) resume];
    }
}

void VideoPlayer::stop()
{
    if (! _videoURL.empty())
    {
        [((UIVideoViewWrapperIos*)_videoView) stop];
    }
}

void VideoPlayer::seekTo(float sec)
{
    if (! _videoURL.empty())
    {
        [((UIVideoViewWrapperIos*)_videoView) seekTo:sec];
    }
}

void VideoPlayer::setVolume(float value)
{
    if (! _videoURL.empty())
    {
        [((UIVideoViewWrapperIos*)_videoView) setVolume:value];
    }
}

void VideoPlayer::setBrightness(float value)
{
    if (! _videoURL.empty())
    {
        [((UIVideoViewWrapperIos*)_videoView) setBrightness:value];
    }
}

bool VideoPlayer::isPlaying() const
{
    return _isPlaying;
}

int VideoPlayer::getVideoDuration() const
{
   return [((UIVideoViewWrapperIos*)_videoView) getVideoDuration];

}
int VideoPlayer::getVideoCurrentTime() const
{
    return [((UIVideoViewWrapperIos*)_videoView) getVideoCurrentTime];
    
}

void VideoPlayer::setVisible(bool visible)
{
    cocos2d::ui::Widget::setVisible(visible);

    if (!visible)
    {
        [((UIVideoViewWrapperIos*)_videoView) setVisible:NO];
    }
    else if(isRunning())
    {
        [((UIVideoViewWrapperIos*)_videoView) setVisible:YES];
    }
}

void VideoPlayer::onEnter()
{
    Widget::onEnter();
    if (isVisible())
    {
        [((UIVideoViewWrapperIos*)_videoView) setVisible: YES];
    }
}

void VideoPlayer::onExit()
{
    Widget::onExit();
    [((UIVideoViewWrapperIos*)_videoView) setVisible: NO];
}

void VideoPlayer::addEventListener(const VideoPlayer::ccVideoPlayerCallback& callback)
{
    _eventCallback = callback;
}

void VideoPlayer::onPlayEvent(int event)
{
    if (event == (int)VideoPlayer::EventType::PLAYING) {
        _isPlaying = true;
    } else {
        _isPlaying = false;
    }

    if (_eventCallback)
    {
        _eventCallback(this, (VideoPlayer::EventType)event);
    }
}

cocos2d::ui::Widget* VideoPlayer::createCloneInstance()
{
    return VideoPlayer::create();
}

void VideoPlayer::copySpecialProperties(Widget *widget)
{
    VideoPlayer* videoPlayer = dynamic_cast<VideoPlayer*>(widget);
    if (videoPlayer)
    {
        _isPlaying = videoPlayer->_isPlaying;
        _fullScreenEnabled = videoPlayer->_fullScreenEnabled;
        _fullScreenDirty = videoPlayer->_fullScreenDirty;
        _videoURL = videoPlayer->_videoURL;
        _keepAspectRatioEnabled = videoPlayer->_keepAspectRatioEnabled;
        _videoSource = videoPlayer->_videoSource;
        _videoPlayerIndex = videoPlayer->_videoPlayerIndex;
        _eventCallback = videoPlayer->_eventCallback;
        _videoView = videoPlayer->_videoView;
    }
}

#endif

