#!/usr/bin/env bash
# ==============================================================================
#  Alazab Assistant - Build Script for Android APK & AAB
#  اسكربت بناء حزم التطبيق (APK & AAB) لمشروع مساعد العزب
# ==============================================================================

set -e

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

APP_NAME="AzabAssistant"
VERSION_NAME="1.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DIST_DIR="$SCRIPT_DIR/dist"
mkdir -p "$DIST_DIR"

print_header() {
    echo -e "${CYAN}================================================================${NC}"
    echo -e "${BOLD}${BLUE}   مؤسسة العزب - اسكربت بناء التطبيق (APK & AAB Builder)${NC}"
    echo -e "${CYAN}================================================================${NC}"
    echo ""
}

print_help() {
    print_header
    echo -e "${BOLD}الاستخدام (Usage):${NC}"
    echo "  ./build.sh [الخيار | OPTION]"
    echo ""
    echo -e "${BOLD}الخيارات المتاحة (Options):${NC}"
    echo "  all      : بناء كل من ملفات APK و AAB (الوضع الافتراضي)"
    echo "  apk      : بناء ملف APK فقط (قابل للتثبيت المباشر على الهاتف)"
    echo "  aab      : بناء ملف AAB فقط (Android App Bundle لمتجر Google Play)"
    echo "  release  : بناء حزم الإنتاج Release (APK و AAB)"
    echo "  clean    : تنظيف مخرجات البناء السابقة"
    echo "  help     : عرض هذه التعليمات"
    echo ""
    echo -e "${BOLD}أمثلة سريعة (Examples):${NC}"
    echo "  ./build.sh          # بناء APK و AAB معاً"
    echo "  ./build.sh apk      # بناء ملف APK فقط"
    echo "  ./build.sh aab      # بناء ملف AAB فقط"
    echo ""
}

check_prerequisites() {
    echo -e "${YELLOW}🔍 التحقق من المتطلبات وملفات التوقيع...${NC}"

    # Check keystore
    if [ ! -f "debug.keystore" ]; then
        if [ -f "debug.keystore.base64" ]; then
            echo -e "${CYAN}ℹ️  استخراج debug.keystore من debug.keystore.base64...${NC}"
            base64 -d debug.keystore.base64 > debug.keystore
        else
            echo -e "${YELLOW}⚠️  ملف debug.keystore غير موجود، سيقوم Gradle بإنشائه تلقائياً.${NC}"
        fi
    fi

    # Determine gradle executable (system gradle or gradlew wrapper)
    if command -v gradle &> /dev/null; then
        GRADLE_CMD="gradle"
    elif [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        GRADLE_CMD="./gradlew"
    else
        echo -e "${RED}❌ خطأ: لم يتم العثور على أداة Gradle أو gradlew في النظام.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ الأداة المستخدمة: $GRADLE_CMD${NC}"
    echo ""
}

copy_and_report_artifact() {
    local src_file="$1"
    local dest_filename="$2"
    local artifact_type="$3"

    if [ -f "$src_file" ]; then
        local dest_file="$DIST_DIR/$dest_filename"
        cp -f "$src_file" "$dest_file"
        local file_size=$(du -h "$dest_file" | cut -f1)

        echo -e "${GREEN}✅ تم بنجاح بناء $artifact_type!${NC}"
        echo -e "   📁 المسار: ${BOLD}$dest_file${NC}"
        echo -e "   ⚖️  الحجم:  ${CYAN}$file_size${NC}"
        echo ""
    else
        echo -e "${RED}⚠️  لم يتم العثور على الملف في المسار: $src_file${NC}"
    fi
}

build_apk() {
    local variant="${1:-debug}"
    local task_name=":app:assembleDebug"
    local src_path="app/build/outputs/apk/debug/app-debug.apk"
    local out_name="${APP_NAME}-v${VERSION_NAME}-debug.apk"

    if [ "$variant" == "release" ]; then
        task_name=":app:assembleRelease"
        src_path="app/build/outputs/apk/release/app-release.apk"
        out_name="${APP_NAME}-v${VERSION_NAME}-release.apk"
    fi

    echo -e "${BLUE}🚀 بدء بناء ملف APK ($variant)...${NC}"
    $GRADLE_CMD "$task_name"
    copy_and_report_artifact "$src_path" "$out_name" "ملف التثبيت (APK)"
}

build_aab() {
    local variant="${1:-debug}"
    local task_name=":app:bundleDebug"
    local src_path="app/build/outputs/bundle/debug/app-debug.aab"
    local out_name="${APP_NAME}-v${VERSION_NAME}-debug.aab"

    if [ "$variant" == "release" ]; then
        task_name=":app:bundleRelease"
        src_path="app/build/outputs/bundle/release/app-release.aab"
        out_name="${APP_NAME}-v${VERSION_NAME}-release.aab"
    fi

    echo -e "${BLUE}🚀 بدء بناء حزمة المتجر AAB ($variant)...${NC}"
    $GRADLE_CMD "$task_name"
    copy_and_report_artifact "$src_path" "$out_name" "حزمة المتجر (AAB)"
}

# Main Execution Flow
TARGET="${1:-all}"

case "$TARGET" in
    help|--help|-h)
        print_help
        exit 0
        ;;
    clean)
        print_header
        check_prerequisites
        echo -e "${YELLOW}🧹 جاري تنظيف مجلدات البناء...${NC}"
        $GRADLE_CMD clean
        rm -rf "$DIST_DIR"/*
        echo -e "${GREEN}✓ تم التنظيف بنجاح.${NC}"
        exit 0
        ;;
    apk)
        print_header
        check_prerequisites
        build_apk "debug"
        ;;
    aab)
        print_header
        check_prerequisites
        build_aab "debug"
        ;;
    release)
        print_header
        check_prerequisites
        build_apk "release" || echo -e "${YELLOW}ملاحظة: يتطلب بناء Release توفير مفتاح my-upload-key.jks أو متغيرات KEYSTORE_PATH.${NC}"
        build_aab "release" || true
        ;;
    all|*)
        print_header
        check_prerequisites
        build_apk "debug"
        build_aab "debug"
        ;;
esac

echo -e "${CYAN}================================================================${NC}"
echo -e "${BOLD}${GREEN}🎉 اكتملت عملية البناء بنجاح!${NC}"
echo -e "📂 جميع الملفات الجاهزة محفوظة في المجلد: ${BOLD}$DIST_DIR${NC}"
echo ""
echo -e "${BOLD}📱 لتثبيت التطبيق على هاتفك المتصل عبر ADB:${NC}"
echo -e "   ${CYAN}adb install -r dist/${APP_NAME}-v${VERSION_NAME}-debug.apk${NC}"
echo -e "${CYAN}================================================================${NC}"
