#!/bin/sh
# Compiles TvActivity for Android's VM into src/android/resources, where the mobile profile picks it
# up. Needs only a JDK: the Android API comes from Maven Central, and Gluon's MainActivity is
# stubbed, since all that is needed of it is that it is an Activity.
set -e
cd "$(dirname "$0")/../../.."
work=$(mktemp -d)
./mvnw -q dependency:copy -Dartifact=com.google.android:android:4.1.1.4 -DoutputDirectory="$work"
mkdir -p "$work/stub/com/gluonhq/helloandroid" "$work/out"
echo 'package com.gluonhq.helloandroid; public class MainActivity extends android.app.Activity { }' \
    > "$work/stub/com/gluonhq/helloandroid/MainActivity.java"
javac --release 8 -Xlint:-options -cp "$work/android-4.1.1.4.jar" -d "$work/out" \
    "$work/stub/com/gluonhq/helloandroid/MainActivity.java" \
    src/android/dalvik/com/hashimjacobs/spacecase/TvActivity.java
dest=src/android/resources/META-INF/substrate/dalvik/precompiled/classes/com/hashimjacobs/spacecase
mkdir -p "$dest"
cp "$work/out/com/hashimjacobs/spacecase/TvActivity.class" "$dest/"
rm -rf "$work"
