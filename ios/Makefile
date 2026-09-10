TARGET := iphone:clang:16.5:15.0
ARCHS := arm64
INSTALL_TARGET_PROCESSES = XMUTimeTable

include $(THEOS)/makefiles/common.mk

APPLICATION_NAME = XMUTimeTable

XMUTimeTable_FILES = \
	Shared/WidgetModels.swift \
	XMUTimeTable/Services/WidgetBridge.swift \
	XMUTimeTable/XMUTimeTableApp.swift \
	XMUTimeTable/ContentView.swift \
	XMUTimeTable/Models/Course.swift \
	XMUTimeTable/Models/TimeTable.swift \
	XMUTimeTable/Services/DateUtils.swift \
	XMUTimeTable/Services/TimeTableUtils.swift \
	XMUTimeTable/Services/SleepyFormat.swift \
	XMUTimeTable/Services/SleepyParser.swift \
	XMUTimeTable/Services/SleepyExporter.swift \
	XMUTimeTable/Services/DatabaseService.swift \
	XMUTimeTable/Services/AppState.swift \
	XMUTimeTable/Services/XmuJw.swift \
	XMUTimeTable/Services/AesCrypto.swift \
	XMUTimeTable/Services/XmuAutoLoginClient.swift \
	XMUTimeTable/Services/NotificationService.swift \
	XMUTimeTable/Services/ICSExporter.swift \
	XMUTimeTable/Views/TodayView.swift \
	XMUTimeTable/Views/WeekView.swift \
	XMUTimeTable/Views/GridView.swift \
	XMUTimeTable/Views/CourseEditView.swift \
	XMUTimeTable/Views/CourseDetailSheet.swift \
	XMUTimeTable/Views/ImportExportView.swift \
	XMUTimeTable/Views/JWImportView.swift \
	XMUTimeTable/Views/ManageTablesView.swift \
	XMUTimeTable/Views/SettingsView.swift \
	XMUTimeTable/Views/ReminderView.swift \
	XMUTimeTable/Views/AboutView.swift \
	XMUTimeTable/Views/Theme.swift

XMUTimeTable_FRAMEWORKS = UIKit SwiftUI Foundation Security CryptoKit
XMUTimeTable_LIBRARIES = sqlite3
XMUTimeTable_CFLAGS = -fobjc-arc
XMUTimeTable_CODESIGN_FLAGS = -Sentitlements.plist
XMUTimeTable_RESOURCE_DIRS = XMUTimeTable/Resources

APPEX_NAME = XMUTimeTableWidget
XMUTimeTableWidget_FILES = \
	Shared/WidgetModels.swift \
	Widget/XMUTimeTableWidget.swift
XMUTimeTableWidget_FRAMEWORKS = UIKit SwiftUI WidgetKit
XMUTimeTableWidget_CFLAGS = -fobjc-arc
XMUTimeTableWidget_CODESIGN_FLAGS = -Sentitlements-widget.plist
XMUTimeTableWidget_RESOURCE_DIRS = Widget/Resources
XMUTimeTableWidget_INSTALL_PATH = /Applications/XMUTimeTable.app/PlugIns

include $(THEOS_MAKE_PATH)/application.mk
include $(THEOS_MAKE_PATH)/appex.mk
