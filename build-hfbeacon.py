#!/usr/bin/python

from sys import argv
from os import chdir, execlp


def usage():
    print("Usage:\n")
    print("")
    print("help    this list of options")
    print("debug   build a debug version of the app")
    print("release build a release version of the app")

if len(argv) == 2:
    cmd = argv[1]
    if cmd in ['debug', 'release']:
        chdir("/app/src")
        execlp("ant", "-Dsdk.dir=/usr/local/android-sdk", "-Dkey.store=/keys/AndroidAppsKey.keystore", "-Dkey.alias=AndroidApps", cmd)
    else:
        usage()
else:
    usage()
