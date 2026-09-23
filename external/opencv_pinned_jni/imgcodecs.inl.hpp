//
// This file is auto-generated. Please don't modify it!
//

#undef LOG_TAG

#include "opencv2/opencv_modules.hpp"
#ifdef HAVE_OPENCV_IMGCODECS

#include <string>

#include "opencv2/imgcodecs.hpp"

#include "/tmp/opencv-src/modules/imgcodecs/include/opencv2/imgcodecs.hpp"

#define LOG_TAG "org.opencv.imgcodecs"
#include "common.h"

using namespace cv;

/// throw java exception
#undef throwJavaException
#define throwJavaException throwJavaException_imgcodecs
static void throwJavaException(JNIEnv *env, const std::exception *e, const char *method) {
  std::string what = "unknown exception";
  jclass je = 0;

  if(e) {
    std::string exception_type = "std::exception";

    if(dynamic_cast<const cv::Exception*>(e)) {
      exception_type = "cv::Exception";
      je = env->FindClass("org/opencv/core/CvException");
    }

    what = exception_type + ": " + e->what();
  }

  if(!je) je = env->FindClass("java/lang/Exception");
  env->ThrowNew(je, what.c_str());

  LOGE("%s caught %s", method, what.c_str());
  (void)method;        // avoid "unused" warning
}

extern "C" {


//
//   cv::Animation::Animation(int loopCount = 0, Scalar bgColor = Scalar())
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_Animation_10 (JNIEnv*, jclass, jint, jdouble, jdouble, jdouble, jdouble);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_Animation_10
  (JNIEnv* env, jclass , jint loopCount, jdouble bgColor_val0, jdouble bgColor_val1, jdouble bgColor_val2, jdouble bgColor_val3)
{
    
    static const char method_name[] = "imgcodecs::Animation_10()";
    try {
        LOGD("%s", method_name);
        Scalar bgColor(bgColor_val0, bgColor_val1, bgColor_val2, bgColor_val3);
        cv::Animation* _retval_ = new cv::Animation( (int)loopCount, bgColor );
        return (jlong) _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_Animation_11 (JNIEnv*, jclass, jint);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_Animation_11
  (JNIEnv* env, jclass , jint loopCount)
{
    
    static const char method_name[] = "imgcodecs::Animation_11()";
    try {
        LOGD("%s", method_name);
        cv::Animation* _retval_ = new cv::Animation( (int)loopCount );
        return (jlong) _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_Animation_12 (JNIEnv*, jclass);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_Animation_12
  (JNIEnv* env, jclass )
{
    
    static const char method_name[] = "imgcodecs::Animation_12()";
    try {
        LOGD("%s", method_name);
        cv::Animation* _retval_ = new cv::Animation();
        return (jlong) _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
// int Animation::loop_count
//

JNIEXPORT jint JNICALL Java_org_opencv_imgcodecs_Animation_get_1loop_1count_10 (JNIEnv*, jclass, jlong);

JNIEXPORT jint JNICALL Java_org_opencv_imgcodecs_Animation_get_1loop_1count_10
  (JNIEnv* env, jclass , jlong self)
{
    
    static const char method_name[] = "imgcodecs::get_1loop_1count_10()";
    try {
        LOGD("%s", method_name);
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        return me->loop_count;//();
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
// void Animation::loop_count
//

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1loop_1count_10 (JNIEnv*, jclass, jlong, jint);

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1loop_1count_10
  (JNIEnv* env, jclass , jlong self, jint loop_count)
{
    
    static const char method_name[] = "imgcodecs::set_1loop_1count_10()";
    try {
        LOGD("%s", method_name);
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        me->loop_count = ( (int)loop_count );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
}



//
// Scalar Animation::bgcolor
//

JNIEXPORT jdoubleArray JNICALL Java_org_opencv_imgcodecs_Animation_get_1bgcolor_10 (JNIEnv*, jclass, jlong);

JNIEXPORT jdoubleArray JNICALL Java_org_opencv_imgcodecs_Animation_get_1bgcolor_10
  (JNIEnv* env, jclass , jlong self)
{
    
    static const char method_name[] = "imgcodecs::get_1bgcolor_10()";
    try {
        LOGD("%s", method_name);
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        Scalar _retval_ = me->bgcolor;//();
        jdoubleArray _da_retval_ = env->NewDoubleArray(4);  jdouble _tmp_retval_[4] = {(jdouble)_retval_.val[0], (jdouble)_retval_.val[1], (jdouble)_retval_.val[2], (jdouble)_retval_.val[3]}; env->SetDoubleArrayRegion(_da_retval_, 0, 4, _tmp_retval_);
        return _da_retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
// void Animation::bgcolor
//

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1bgcolor_10 (JNIEnv*, jclass, jlong, jdouble, jdouble, jdouble, jdouble);

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1bgcolor_10
  (JNIEnv* env, jclass , jlong self, jdouble bgcolor_val0, jdouble bgcolor_val1, jdouble bgcolor_val2, jdouble bgcolor_val3)
{
    
    static const char method_name[] = "imgcodecs::set_1bgcolor_10()";
    try {
        LOGD("%s", method_name);
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        Scalar bgcolor(bgcolor_val0, bgcolor_val1, bgcolor_val2, bgcolor_val3);
        me->bgcolor = ( bgcolor );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
}



//
// vector_int Animation::durations
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_get_1durations_10 (JNIEnv*, jclass, jlong);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_get_1durations_10
  (JNIEnv* env, jclass , jlong self)
{
    
    static const char method_name[] = "imgcodecs::get_1durations_10()";
    try {
        LOGD("%s", method_name);
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        std::vector<int> _ret_val_vector_ = me->durations;//();
        Mat* _retval_ = new Mat();
        vector_int_to_Mat(_ret_val_vector_, *_retval_);
        return (jlong) _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
// void Animation::durations
//

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1durations_10 (JNIEnv*, jclass, jlong, jlong);

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1durations_10
  (JNIEnv* env, jclass , jlong self, jlong durations_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::set_1durations_10()";
    try {
        LOGD("%s", method_name);
        std::vector<int> durations;
        Mat& durations_mat = *((Mat*)durations_mat_nativeObj);
        Mat_to_vector_int( durations_mat, durations );
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        me->durations = ( durations );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
}



//
// vector_Mat Animation::frames
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_get_1frames_10 (JNIEnv*, jclass, jlong);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_get_1frames_10
  (JNIEnv* env, jclass , jlong self)
{
    
    static const char method_name[] = "imgcodecs::get_1frames_10()";
    try {
        LOGD("%s", method_name);
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        std::vector<Mat> _ret_val_vector_ = me->frames;//();
        Mat* _retval_ = new Mat();
        vector_Mat_to_Mat(_ret_val_vector_, *_retval_);
        return (jlong) _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
// void Animation::frames
//

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1frames_10 (JNIEnv*, jclass, jlong, jlong);

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1frames_10
  (JNIEnv* env, jclass , jlong self, jlong frames_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::set_1frames_10()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> frames;
        Mat& frames_mat = *((Mat*)frames_mat_nativeObj);
        Mat_to_vector_Mat( frames_mat, frames );
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        me->frames = ( frames );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
}



//
// Mat Animation::still_image
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_get_1still_1image_10 (JNIEnv*, jclass, jlong);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Animation_get_1still_1image_10
  (JNIEnv* env, jclass , jlong self)
{
    
    static const char method_name[] = "imgcodecs::get_1still_1image_10()";
    try {
        LOGD("%s", method_name);
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        cv::Mat _retval_ = me->still_image;//();
        return (jlong) new cv::Mat(_retval_);
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
// void Animation::still_image
//

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1still_1image_10 (JNIEnv*, jclass, jlong, jlong);

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_set_1still_1image_10
  (JNIEnv* env, jclass , jlong self, jlong still_image_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::set_1still_1image_10()";
    try {
        LOGD("%s", method_name);
        cv::Animation* me = (cv::Animation*) self; //TODO: check for NULL
        Mat& still_image = *((Mat*)still_image_nativeObj);
        me->still_image = ( still_image );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
}



//
//  native support for java finalize() or cleaner
//  static void cv::Animation::delete( __int64 self )
//
JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_delete(JNIEnv*, jclass, jlong);

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Animation_delete
  (JNIEnv*, jclass, jlong self)
{
    delete (cv::Animation*) self;
}


//
//  Mat cv::imread(String filename, int flags = IMREAD_COLOR_BGR)
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imread_10 (JNIEnv*, jclass, jstring, jint);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imread_10
  (JNIEnv* env, jclass , jstring filename, jint flags)
{
    
    static const char method_name[] = "imgcodecs::imread_10()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        cv::Mat _retval_ = cv::imread( n_filename, (int)flags );
        return (jlong) new cv::Mat(_retval_);
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imread_11 (JNIEnv*, jclass, jstring);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imread_11
  (JNIEnv* env, jclass , jstring filename)
{
    
    static const char method_name[] = "imgcodecs::imread_11()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        cv::Mat _retval_ = cv::imread( n_filename );
        return (jlong) new cv::Mat(_retval_);
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  void cv::imread(String filename, Mat& dst, int flags = IMREAD_COLOR_BGR)
//

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imread_12 (JNIEnv*, jclass, jstring, jlong, jint);

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imread_12
  (JNIEnv* env, jclass , jstring filename, jlong dst_nativeObj, jint flags)
{
    
    static const char method_name[] = "imgcodecs::imread_12()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        Mat& dst = *((Mat*)dst_nativeObj);
        cv::imread( n_filename, dst, (int)flags );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
}



JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imread_13 (JNIEnv*, jclass, jstring, jlong);

JNIEXPORT void JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imread_13
  (JNIEnv* env, jclass , jstring filename, jlong dst_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imread_13()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        Mat& dst = *((Mat*)dst_nativeObj);
        cv::imread( n_filename, dst );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
}



//
//  Mat cv::imreadWithMetadata(String filename, vector_int& metadataTypes, vector_Mat& metadata, int flags = IMREAD_ANYCOLOR)
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadWithMetadata_10 (JNIEnv*, jclass, jstring, jlong, jlong, jint);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadWithMetadata_10
  (JNIEnv* env, jclass , jstring filename, jlong metadataTypes_mat_nativeObj, jlong metadata_mat_nativeObj, jint flags)
{
    
    static const char method_name[] = "imgcodecs::imreadWithMetadata_10()";
    try {
        LOGD("%s", method_name);
        std::vector<int> metadataTypes;
        Mat& metadataTypes_mat = *((Mat*)metadataTypes_mat_nativeObj);
        std::vector<Mat> metadata;
        Mat& metadata_mat = *((Mat*)metadata_mat_nativeObj);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        cv::Mat _retval_ = cv::imreadWithMetadata( n_filename, metadataTypes, metadata, (int)flags );
        vector_int_to_Mat( metadataTypes, metadataTypes_mat );
        vector_Mat_to_Mat( metadata, metadata_mat );
        return (jlong) new cv::Mat(_retval_);
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadWithMetadata_11 (JNIEnv*, jclass, jstring, jlong, jlong);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadWithMetadata_11
  (JNIEnv* env, jclass , jstring filename, jlong metadataTypes_mat_nativeObj, jlong metadata_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imreadWithMetadata_11()";
    try {
        LOGD("%s", method_name);
        std::vector<int> metadataTypes;
        Mat& metadataTypes_mat = *((Mat*)metadataTypes_mat_nativeObj);
        std::vector<Mat> metadata;
        Mat& metadata_mat = *((Mat*)metadata_mat_nativeObj);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        cv::Mat _retval_ = cv::imreadWithMetadata( n_filename, metadataTypes, metadata );
        vector_int_to_Mat( metadataTypes, metadataTypes_mat );
        vector_Mat_to_Mat( metadata, metadata_mat );
        return (jlong) new cv::Mat(_retval_);
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imreadmulti(String filename, vector_Mat& mats, int flags = IMREAD_ANYCOLOR)
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadmulti_10 (JNIEnv*, jclass, jstring, jlong, jint);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadmulti_10
  (JNIEnv* env, jclass , jstring filename, jlong mats_mat_nativeObj, jint flags)
{
    
    static const char method_name[] = "imgcodecs::imreadmulti_10()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> mats;
        Mat& mats_mat = *((Mat*)mats_mat_nativeObj);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        bool _retval_ = cv::imreadmulti( n_filename, mats, (int)flags );
        vector_Mat_to_Mat( mats, mats_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadmulti_11 (JNIEnv*, jclass, jstring, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadmulti_11
  (JNIEnv* env, jclass , jstring filename, jlong mats_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imreadmulti_11()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> mats;
        Mat& mats_mat = *((Mat*)mats_mat_nativeObj);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        bool _retval_ = cv::imreadmulti( n_filename, mats );
        vector_Mat_to_Mat( mats, mats_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imreadmulti(String filename, vector_Mat& mats, int start, int count, int flags = IMREAD_ANYCOLOR)
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadmulti_12 (JNIEnv*, jclass, jstring, jlong, jint, jint, jint);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadmulti_12
  (JNIEnv* env, jclass , jstring filename, jlong mats_mat_nativeObj, jint start, jint count, jint flags)
{
    
    static const char method_name[] = "imgcodecs::imreadmulti_12()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> mats;
        Mat& mats_mat = *((Mat*)mats_mat_nativeObj);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        bool _retval_ = cv::imreadmulti( n_filename, mats, (int)start, (int)count, (int)flags );
        vector_Mat_to_Mat( mats, mats_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadmulti_13 (JNIEnv*, jclass, jstring, jlong, jint, jint);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadmulti_13
  (JNIEnv* env, jclass , jstring filename, jlong mats_mat_nativeObj, jint start, jint count)
{
    
    static const char method_name[] = "imgcodecs::imreadmulti_13()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> mats;
        Mat& mats_mat = *((Mat*)mats_mat_nativeObj);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        bool _retval_ = cv::imreadmulti( n_filename, mats, (int)start, (int)count );
        vector_Mat_to_Mat( mats, mats_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imreadanimation(String filename, Animation& animation, int start = 0, int count = INT16_MAX)
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadanimation_10 (JNIEnv*, jclass, jstring, jlong, jint, jint);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadanimation_10
  (JNIEnv* env, jclass , jstring filename, jlong animation_nativeObj, jint start, jint count)
{
    
    static const char method_name[] = "imgcodecs::imreadanimation_10()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imreadanimation( n_filename, (*(cv::Animation*)animation_nativeObj), (int)start, (int)count );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadanimation_11 (JNIEnv*, jclass, jstring, jlong, jint);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadanimation_11
  (JNIEnv* env, jclass , jstring filename, jlong animation_nativeObj, jint start)
{
    
    static const char method_name[] = "imgcodecs::imreadanimation_11()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imreadanimation( n_filename, (*(cv::Animation*)animation_nativeObj), (int)start );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadanimation_12 (JNIEnv*, jclass, jstring, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imreadanimation_12
  (JNIEnv* env, jclass , jstring filename, jlong animation_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imreadanimation_12()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imreadanimation( n_filename, (*(cv::Animation*)animation_nativeObj) );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imdecodeanimation(Mat buf, Animation& animation, int start = 0, int count = INT16_MAX)
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeanimation_10 (JNIEnv*, jclass, jlong, jlong, jint, jint);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeanimation_10
  (JNIEnv* env, jclass , jlong buf_nativeObj, jlong animation_nativeObj, jint start, jint count)
{
    
    static const char method_name[] = "imgcodecs::imdecodeanimation_10()";
    try {
        LOGD("%s", method_name);
        Mat& buf = *((Mat*)buf_nativeObj);
        return cv::imdecodeanimation( buf, (*(cv::Animation*)animation_nativeObj), (int)start, (int)count );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeanimation_11 (JNIEnv*, jclass, jlong, jlong, jint);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeanimation_11
  (JNIEnv* env, jclass , jlong buf_nativeObj, jlong animation_nativeObj, jint start)
{
    
    static const char method_name[] = "imgcodecs::imdecodeanimation_11()";
    try {
        LOGD("%s", method_name);
        Mat& buf = *((Mat*)buf_nativeObj);
        return cv::imdecodeanimation( buf, (*(cv::Animation*)animation_nativeObj), (int)start );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeanimation_12 (JNIEnv*, jclass, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeanimation_12
  (JNIEnv* env, jclass , jlong buf_nativeObj, jlong animation_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imdecodeanimation_12()";
    try {
        LOGD("%s", method_name);
        Mat& buf = *((Mat*)buf_nativeObj);
        return cv::imdecodeanimation( buf, (*(cv::Animation*)animation_nativeObj) );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imwriteanimation(String filename, Animation animation, vector_int params = std::vector<int>())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwriteanimation_10 (JNIEnv*, jclass, jstring, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwriteanimation_10
  (JNIEnv* env, jclass , jstring filename, jlong animation_nativeObj, jlong params_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imwriteanimation_10()";
    try {
        LOGD("%s", method_name);
        std::vector<int> params;
        Mat& params_mat = *((Mat*)params_mat_nativeObj);
        Mat_to_vector_int( params_mat, params );
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imwriteanimation( n_filename, (*(cv::Animation*)animation_nativeObj), params );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwriteanimation_11 (JNIEnv*, jclass, jstring, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwriteanimation_11
  (JNIEnv* env, jclass , jstring filename, jlong animation_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imwriteanimation_11()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imwriteanimation( n_filename, (*(cv::Animation*)animation_nativeObj) );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imencodeanimation(String ext, Animation animation, vector_uchar& buf, vector_int params = std::vector<int>())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodeanimation_10 (JNIEnv*, jclass, jstring, jlong, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodeanimation_10
  (JNIEnv* env, jclass , jstring ext, jlong animation_nativeObj, jlong buf_mat_nativeObj, jlong params_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imencodeanimation_10()";
    try {
        LOGD("%s", method_name);
        std::vector<uchar> buf;
        Mat& buf_mat = *((Mat*)buf_mat_nativeObj);
        std::vector<int> params;
        Mat& params_mat = *((Mat*)params_mat_nativeObj);
        Mat_to_vector_int( params_mat, params );
        const char* utf_ext = env->GetStringUTFChars(ext, 0); String n_ext( utf_ext ? utf_ext : "" ); env->ReleaseStringUTFChars(ext, utf_ext);
        bool _retval_ = cv::imencodeanimation( n_ext, (*(cv::Animation*)animation_nativeObj), buf, params );
        vector_uchar_to_Mat( buf, buf_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodeanimation_11 (JNIEnv*, jclass, jstring, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodeanimation_11
  (JNIEnv* env, jclass , jstring ext, jlong animation_nativeObj, jlong buf_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imencodeanimation_11()";
    try {
        LOGD("%s", method_name);
        std::vector<uchar> buf;
        Mat& buf_mat = *((Mat*)buf_mat_nativeObj);
        const char* utf_ext = env->GetStringUTFChars(ext, 0); String n_ext( utf_ext ? utf_ext : "" ); env->ReleaseStringUTFChars(ext, utf_ext);
        bool _retval_ = cv::imencodeanimation( n_ext, (*(cv::Animation*)animation_nativeObj), buf );
        vector_uchar_to_Mat( buf, buf_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  size_t cv::imcount(String filename, int flags = IMREAD_ANYCOLOR)
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imcount_10 (JNIEnv*, jclass, jstring, jint);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imcount_10
  (JNIEnv* env, jclass , jstring filename, jint flags)
{
    
    static const char method_name[] = "imgcodecs::imcount_10()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imcount( n_filename, (int)flags );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imcount_11 (JNIEnv*, jclass, jstring);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imcount_11
  (JNIEnv* env, jclass , jstring filename)
{
    
    static const char method_name[] = "imgcodecs::imcount_11()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imcount( n_filename );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imwrite(String filename, Mat img, vector_int params = std::vector<int>())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwrite_10 (JNIEnv*, jclass, jstring, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwrite_10
  (JNIEnv* env, jclass , jstring filename, jlong img_nativeObj, jlong params_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imwrite_10()";
    try {
        LOGD("%s", method_name);
        std::vector<int> params;
        Mat& params_mat = *((Mat*)params_mat_nativeObj);
        Mat_to_vector_int( params_mat, params );
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        Mat& img = *((Mat*)img_nativeObj);
        return cv::imwrite( n_filename, img, params );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwrite_11 (JNIEnv*, jclass, jstring, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwrite_11
  (JNIEnv* env, jclass , jstring filename, jlong img_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imwrite_11()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        Mat& img = *((Mat*)img_nativeObj);
        return cv::imwrite( n_filename, img );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imwriteWithMetadata(String filename, Mat img, vector_int metadataTypes, vector_Mat metadata, vector_int params = std::vector<int>())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwriteWithMetadata_10 (JNIEnv*, jclass, jstring, jlong, jlong, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwriteWithMetadata_10
  (JNIEnv* env, jclass , jstring filename, jlong img_nativeObj, jlong metadataTypes_mat_nativeObj, jlong metadata_mat_nativeObj, jlong params_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imwriteWithMetadata_10()";
    try {
        LOGD("%s", method_name);
        std::vector<int> metadataTypes;
        Mat& metadataTypes_mat = *((Mat*)metadataTypes_mat_nativeObj);
        Mat_to_vector_int( metadataTypes_mat, metadataTypes );
        std::vector<Mat> metadata;
        Mat& metadata_mat = *((Mat*)metadata_mat_nativeObj);
        Mat_to_vector_Mat( metadata_mat, metadata );
        std::vector<int> params;
        Mat& params_mat = *((Mat*)params_mat_nativeObj);
        Mat_to_vector_int( params_mat, params );
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        Mat& img = *((Mat*)img_nativeObj);
        return cv::imwriteWithMetadata( n_filename, img, metadataTypes, metadata, params );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwriteWithMetadata_11 (JNIEnv*, jclass, jstring, jlong, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwriteWithMetadata_11
  (JNIEnv* env, jclass , jstring filename, jlong img_nativeObj, jlong metadataTypes_mat_nativeObj, jlong metadata_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imwriteWithMetadata_11()";
    try {
        LOGD("%s", method_name);
        std::vector<int> metadataTypes;
        Mat& metadataTypes_mat = *((Mat*)metadataTypes_mat_nativeObj);
        Mat_to_vector_int( metadataTypes_mat, metadataTypes );
        std::vector<Mat> metadata;
        Mat& metadata_mat = *((Mat*)metadata_mat_nativeObj);
        Mat_to_vector_Mat( metadata_mat, metadata );
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        Mat& img = *((Mat*)img_nativeObj);
        return cv::imwriteWithMetadata( n_filename, img, metadataTypes, metadata );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imwritemulti(String filename, vector_Mat img, vector_int params = std::vector<int>())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwritemulti_10 (JNIEnv*, jclass, jstring, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwritemulti_10
  (JNIEnv* env, jclass , jstring filename, jlong img_mat_nativeObj, jlong params_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imwritemulti_10()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> img;
        Mat& img_mat = *((Mat*)img_mat_nativeObj);
        Mat_to_vector_Mat( img_mat, img );
        std::vector<int> params;
        Mat& params_mat = *((Mat*)params_mat_nativeObj);
        Mat_to_vector_int( params_mat, params );
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imwritemulti( n_filename, img, params );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwritemulti_11 (JNIEnv*, jclass, jstring, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imwritemulti_11
  (JNIEnv* env, jclass , jstring filename, jlong img_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imwritemulti_11()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> img;
        Mat& img_mat = *((Mat*)img_mat_nativeObj);
        Mat_to_vector_Mat( img_mat, img );
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::imwritemulti( n_filename, img );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  Mat cv::imdecode(Mat buf, int flags)
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecode_10 (JNIEnv*, jclass, jlong, jint);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecode_10
  (JNIEnv* env, jclass , jlong buf_nativeObj, jint flags)
{
    
    static const char method_name[] = "imgcodecs::imdecode_10()";
    try {
        LOGD("%s", method_name);
        Mat& buf = *((Mat*)buf_nativeObj);
        cv::Mat _retval_ = cv::imdecode( buf, (int)flags );
        return (jlong) new cv::Mat(_retval_);
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  Mat cv::imdecodeWithMetadata(Mat buf, vector_int& metadataTypes, vector_Mat& metadata, int flags = IMREAD_ANYCOLOR)
//

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeWithMetadata_10 (JNIEnv*, jclass, jlong, jlong, jlong, jint);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeWithMetadata_10
  (JNIEnv* env, jclass , jlong buf_nativeObj, jlong metadataTypes_mat_nativeObj, jlong metadata_mat_nativeObj, jint flags)
{
    
    static const char method_name[] = "imgcodecs::imdecodeWithMetadata_10()";
    try {
        LOGD("%s", method_name);
        std::vector<int> metadataTypes;
        Mat& metadataTypes_mat = *((Mat*)metadataTypes_mat_nativeObj);
        std::vector<Mat> metadata;
        Mat& metadata_mat = *((Mat*)metadata_mat_nativeObj);
        Mat& buf = *((Mat*)buf_nativeObj);
        cv::Mat _retval_ = cv::imdecodeWithMetadata( buf, metadataTypes, metadata, (int)flags );
        vector_int_to_Mat( metadataTypes, metadataTypes_mat );
        vector_Mat_to_Mat( metadata, metadata_mat );
        return (jlong) new cv::Mat(_retval_);
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeWithMetadata_11 (JNIEnv*, jclass, jlong, jlong, jlong);

JNIEXPORT jlong JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodeWithMetadata_11
  (JNIEnv* env, jclass , jlong buf_nativeObj, jlong metadataTypes_mat_nativeObj, jlong metadata_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imdecodeWithMetadata_11()";
    try {
        LOGD("%s", method_name);
        std::vector<int> metadataTypes;
        Mat& metadataTypes_mat = *((Mat*)metadataTypes_mat_nativeObj);
        std::vector<Mat> metadata;
        Mat& metadata_mat = *((Mat*)metadata_mat_nativeObj);
        Mat& buf = *((Mat*)buf_nativeObj);
        cv::Mat _retval_ = cv::imdecodeWithMetadata( buf, metadataTypes, metadata );
        vector_int_to_Mat( metadataTypes, metadataTypes_mat );
        vector_Mat_to_Mat( metadata, metadata_mat );
        return (jlong) new cv::Mat(_retval_);
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imdecodemulti(Mat buf, int flags, vector_Mat& mats, Range range = Range::all())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodemulti_10 (JNIEnv*, jclass, jlong, jint, jlong, jint, jint);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodemulti_10
  (JNIEnv* env, jclass , jlong buf_nativeObj, jint flags, jlong mats_mat_nativeObj, jint range_start, jint range_end)
{
    
    static const char method_name[] = "imgcodecs::imdecodemulti_10()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> mats;
        Mat& mats_mat = *((Mat*)mats_mat_nativeObj);
        Mat& buf = *((Mat*)buf_nativeObj);
        Range range(range_start, range_end);
        bool _retval_ = cv::imdecodemulti( buf, (int)flags, mats, range );
        vector_Mat_to_Mat( mats, mats_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodemulti_11 (JNIEnv*, jclass, jlong, jint, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imdecodemulti_11
  (JNIEnv* env, jclass , jlong buf_nativeObj, jint flags, jlong mats_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imdecodemulti_11()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> mats;
        Mat& mats_mat = *((Mat*)mats_mat_nativeObj);
        Mat& buf = *((Mat*)buf_nativeObj);
        bool _retval_ = cv::imdecodemulti( buf, (int)flags, mats );
        vector_Mat_to_Mat( mats, mats_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imencode(String ext, Mat img, vector_uchar& buf, vector_int params = std::vector<int>())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencode_10 (JNIEnv*, jclass, jstring, jlong, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencode_10
  (JNIEnv* env, jclass , jstring ext, jlong img_nativeObj, jlong buf_mat_nativeObj, jlong params_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imencode_10()";
    try {
        LOGD("%s", method_name);
        std::vector<uchar> buf;
        Mat& buf_mat = *((Mat*)buf_mat_nativeObj);
        std::vector<int> params;
        Mat& params_mat = *((Mat*)params_mat_nativeObj);
        Mat_to_vector_int( params_mat, params );
        const char* utf_ext = env->GetStringUTFChars(ext, 0); String n_ext( utf_ext ? utf_ext : "" ); env->ReleaseStringUTFChars(ext, utf_ext);
        Mat& img = *((Mat*)img_nativeObj);
        bool _retval_ = cv::imencode( n_ext, img, buf, params );
        vector_uchar_to_Mat( buf, buf_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencode_11 (JNIEnv*, jclass, jstring, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencode_11
  (JNIEnv* env, jclass , jstring ext, jlong img_nativeObj, jlong buf_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imencode_11()";
    try {
        LOGD("%s", method_name);
        std::vector<uchar> buf;
        Mat& buf_mat = *((Mat*)buf_mat_nativeObj);
        const char* utf_ext = env->GetStringUTFChars(ext, 0); String n_ext( utf_ext ? utf_ext : "" ); env->ReleaseStringUTFChars(ext, utf_ext);
        Mat& img = *((Mat*)img_nativeObj);
        bool _retval_ = cv::imencode( n_ext, img, buf );
        vector_uchar_to_Mat( buf, buf_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imencodeWithMetadata(String ext, Mat img, vector_int metadataTypes, vector_Mat metadata, vector_uchar& buf, vector_int params = std::vector<int>())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodeWithMetadata_10 (JNIEnv*, jclass, jstring, jlong, jlong, jlong, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodeWithMetadata_10
  (JNIEnv* env, jclass , jstring ext, jlong img_nativeObj, jlong metadataTypes_mat_nativeObj, jlong metadata_mat_nativeObj, jlong buf_mat_nativeObj, jlong params_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imencodeWithMetadata_10()";
    try {
        LOGD("%s", method_name);
        std::vector<int> metadataTypes;
        Mat& metadataTypes_mat = *((Mat*)metadataTypes_mat_nativeObj);
        Mat_to_vector_int( metadataTypes_mat, metadataTypes );
        std::vector<Mat> metadata;
        Mat& metadata_mat = *((Mat*)metadata_mat_nativeObj);
        Mat_to_vector_Mat( metadata_mat, metadata );
        std::vector<uchar> buf;
        Mat& buf_mat = *((Mat*)buf_mat_nativeObj);
        std::vector<int> params;
        Mat& params_mat = *((Mat*)params_mat_nativeObj);
        Mat_to_vector_int( params_mat, params );
        const char* utf_ext = env->GetStringUTFChars(ext, 0); String n_ext( utf_ext ? utf_ext : "" ); env->ReleaseStringUTFChars(ext, utf_ext);
        Mat& img = *((Mat*)img_nativeObj);
        bool _retval_ = cv::imencodeWithMetadata( n_ext, img, metadataTypes, metadata, buf, params );
        vector_uchar_to_Mat( buf, buf_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodeWithMetadata_11 (JNIEnv*, jclass, jstring, jlong, jlong, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodeWithMetadata_11
  (JNIEnv* env, jclass , jstring ext, jlong img_nativeObj, jlong metadataTypes_mat_nativeObj, jlong metadata_mat_nativeObj, jlong buf_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imencodeWithMetadata_11()";
    try {
        LOGD("%s", method_name);
        std::vector<int> metadataTypes;
        Mat& metadataTypes_mat = *((Mat*)metadataTypes_mat_nativeObj);
        Mat_to_vector_int( metadataTypes_mat, metadataTypes );
        std::vector<Mat> metadata;
        Mat& metadata_mat = *((Mat*)metadata_mat_nativeObj);
        Mat_to_vector_Mat( metadata_mat, metadata );
        std::vector<uchar> buf;
        Mat& buf_mat = *((Mat*)buf_mat_nativeObj);
        const char* utf_ext = env->GetStringUTFChars(ext, 0); String n_ext( utf_ext ? utf_ext : "" ); env->ReleaseStringUTFChars(ext, utf_ext);
        Mat& img = *((Mat*)img_nativeObj);
        bool _retval_ = cv::imencodeWithMetadata( n_ext, img, metadataTypes, metadata, buf );
        vector_uchar_to_Mat( buf, buf_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::imencodemulti(String ext, vector_Mat imgs, vector_uchar& buf, vector_int params = std::vector<int>())
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodemulti_10 (JNIEnv*, jclass, jstring, jlong, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodemulti_10
  (JNIEnv* env, jclass , jstring ext, jlong imgs_mat_nativeObj, jlong buf_mat_nativeObj, jlong params_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imencodemulti_10()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> imgs;
        Mat& imgs_mat = *((Mat*)imgs_mat_nativeObj);
        Mat_to_vector_Mat( imgs_mat, imgs );
        std::vector<uchar> buf;
        Mat& buf_mat = *((Mat*)buf_mat_nativeObj);
        std::vector<int> params;
        Mat& params_mat = *((Mat*)params_mat_nativeObj);
        Mat_to_vector_int( params_mat, params );
        const char* utf_ext = env->GetStringUTFChars(ext, 0); String n_ext( utf_ext ? utf_ext : "" ); env->ReleaseStringUTFChars(ext, utf_ext);
        bool _retval_ = cv::imencodemulti( n_ext, imgs, buf, params );
        vector_uchar_to_Mat( buf, buf_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodemulti_11 (JNIEnv*, jclass, jstring, jlong, jlong);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_imencodemulti_11
  (JNIEnv* env, jclass , jstring ext, jlong imgs_mat_nativeObj, jlong buf_mat_nativeObj)
{
    
    static const char method_name[] = "imgcodecs::imencodemulti_11()";
    try {
        LOGD("%s", method_name);
        std::vector<Mat> imgs;
        Mat& imgs_mat = *((Mat*)imgs_mat_nativeObj);
        Mat_to_vector_Mat( imgs_mat, imgs );
        std::vector<uchar> buf;
        Mat& buf_mat = *((Mat*)buf_mat_nativeObj);
        const char* utf_ext = env->GetStringUTFChars(ext, 0); String n_ext( utf_ext ? utf_ext : "" ); env->ReleaseStringUTFChars(ext, utf_ext);
        bool _retval_ = cv::imencodemulti( n_ext, imgs, buf );
        vector_uchar_to_Mat( buf, buf_mat );
        return _retval_;
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::haveImageReader(String filename)
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_haveImageReader_10 (JNIEnv*, jclass, jstring);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_haveImageReader_10
  (JNIEnv* env, jclass , jstring filename)
{
    
    static const char method_name[] = "imgcodecs::haveImageReader_10()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::haveImageReader( n_filename );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}



//
//  bool cv::haveImageWriter(String filename)
//

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_haveImageWriter_10 (JNIEnv*, jclass, jstring);

JNIEXPORT jboolean JNICALL Java_org_opencv_imgcodecs_Imgcodecs_haveImageWriter_10
  (JNIEnv* env, jclass , jstring filename)
{
    
    static const char method_name[] = "imgcodecs::haveImageWriter_10()";
    try {
        LOGD("%s", method_name);
        const char* utf_filename = env->GetStringUTFChars(filename, 0); String n_filename( utf_filename ? utf_filename : "" ); env->ReleaseStringUTFChars(filename, utf_filename);
        return cv::haveImageWriter( n_filename );
    } catch(const std::exception &e) {
        throwJavaException(env, &e, method_name);
    } catch (...) {
        throwJavaException(env, 0, method_name);
    }
    return 0;
}




} // extern "C"

#endif // HAVE_OPENCV_IMGCODECS
