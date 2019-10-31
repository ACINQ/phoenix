# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             secp256k1

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/secp256k1/secp256k1.c
             src/main/cpp/secp256k1/org_bitcoin_NativeSecp256k1.c
             src/main/cpp/secp256k1/org_bitcoin_Secp256k1Context.c)

add_library( # Sets the name of the library.
             sqlitejdbc

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/sqlitejdbc/NativeDB.c
             src/main/cpp/sqlitejdbc/sqlite3.c)
# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

target_compile_options(secp256k1 PUBLIC -DHAVE_CONFIG_H)

target_include_directories(secp256k1 PUBLIC
  src/main/cpp/secp256k1/
)


target_compile_options(sqlitejdbc PUBLIC
-DSQLITE_HAVE_ISNAN
-DSQLITE_HAVE_USLEEP
-DSQLITE_ENABLE_UPDATE_DELETE_LIMIT
-DSQLITE_ENABLE_COLUMN_METADATA
-DSQLITE_CORE
-DSQLITE_ENABLE_FTS3
-DSQLITE_ENABLE_FTS3_PARENTHESIS
-DSQLITE_ENABLE_FTS5
-DSQLITE_ENABLE_JSON1
-DSQLITE_ENABLE_RTREE
-DSQLITE_ENABLE_STAT2
-DSQLITE_THREADSAFE=1
-DSQLITE_DEFAULT_MEMSTATUS=0
-DSQLITE_DEFAULT_FILE_PERMISSIONS=0666
-DSQLITE_MAX_VARIABLE_NUMBER=250000)

target_include_directories(sqlitejdbc PUBLIC
  src/main/cpp/sqlitejdbc/
)

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       secp256k1

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

target_link_libraries( # Specifies the target library.
                       sqlitejdbc

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )
