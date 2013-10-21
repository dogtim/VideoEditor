LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := VideoEditor

LOCAL_JAVA_LIBRARIES :=

LOCAL_PROGUARD_FLAGS := -include $(LOCAL_PATH)/proguard.flags

LOCAL_PROGUARD_ENABLED := disabled

#LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

##################################################
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES :=

include $(BUILD_MULTI_PREBUILT)

include $(call all-makefiles-under, $(LOCAL_PATH))
