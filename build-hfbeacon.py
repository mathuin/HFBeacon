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
        # "-Dsdk.dir=/usr/local/android-sdk",
        execlp("ant", "-Dgen.dir=/app/gen", "-Dkey.store=/keys/AndroidAppsKey.keystore", "-Dkey.alias=twilley.org release key", cmd)
    else:
        usage()
else:
    usage()
