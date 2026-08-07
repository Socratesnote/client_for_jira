## Build
    
### Build parameters
The [build.xml](build.xml) Ant script takes the following parameters:

 * **jdk** - path to Oracle JDK 8. The script uses this JDK to compile sources and run tests.
 
 * **build.number** - optional override for the build number. A built application shows the build number on its
    About screen, and the build script creates a ZIP file named for it.
    When it is not supplied, `genHeader.xml` derives the number from the git commit count
    (`git rev-list --count HEAD`), which makes it stateless and identical on every checkout of a given commit.
    Only `prepareDistribution` reaches that step; `ALL.compile` and `ALL.test` do not.
    When no git count can be established - git is not on `PATH`, or the source is an export rather than a
    repository - the build falls back to `1`. It is deliberately never `0`, because `BuildNumber` treats a major
    of `0` as meaning the build information is unavailable or broken.

### Running the build

[build.sh](build.sh) supplies these parameters. It reads all paths from the
environment and contains none itself:

 * **ANT_HOME** - Apache Ant install directory (the one holding `bin` and `lib`)

 * **JDK8_HOME** - Oracle JDK 8 home directory (the one holding `bin` and `lib`)

 * **BUILD_NUMBER** - optional. When set, it is passed as `build.number` and overrides the git-derived value;
   when unset, the flag is not passed at all, which is what lets the git-derived value apply.

 * **ANT_FILE** - optional, the build file to run. Defaults to `./build.xml`.

Run it from the [ant](.) directory:

```sh
export ANT_HOME=/opt/apache-ant-1.10.7
export JDK8_HOME=/usr/lib/jvm/jdk1.8.0_192

./build.sh
```

Arguments are passed through as Ant targets. Since the per-module targets live in
the generated [runGenerated.xml](runGenerated.xml) rather than in
[build.xml](build.xml), compiling without running tests takes two steps:

```sh
./build.sh generateBuildXml init
ANT_FILE=./runGenerated.xml ./build.sh ALL.compile
```

On Windows the path form must match the shell being used; see the
[README](../README.md) for per-shell instructions.


### Files
 * [build.xml](build.xml) main build script.
 * [build.sh](build.sh) sample shell script which launches a build process and provides it with all required parameters.
 * [genHeader.xml](genHeader.xml), [properties.xml](properties.xml), [runGenerated.xml](runGenerated.xml) supplementary build files.
 * [meta.xml](meta.xml) describes source modules, external libraries, source dependencies and distribution layout.
 * [transform.xsl](transform.xsl) used to transform the [meta.xml](meta.xml) file to an Ant build script.
 * [generated.xml](generated.xml) temporary build script produced by [transform.xsl](transform.xsl) applied to [meta.xml](meta.xml)
 * [lib](lib) directory contains third-party Java libraries required by the build:
    * [javac2.jar](lib/javac2.jar), [bcel.jar](lib/bcel.jar), [asm-all.jar](lib/asm-all.jar) are required to define the _javac2_ task.
    * [saxon9he.jar](lib/saxon9he.jar) is required for XSL transformation of the [meta.xml](meta.xml) file.

## meta.xml file format
The [meta.xml](meta.xml) file describes:

 * Modules - Java Sources.

 * Libraries - external JAR files.
 
 * Product Description - JARs the product distribution consists of and their locations.

### Modules
Java source and resource are organized into modules. Each module is a unit of dependency.
The [meta.xml](meta.xml) file includes the **module** tag for each module.
The **name** parameter of the tag defines the module root directory and module name.

#### Child Tags
 * **depends** describes module dependency on other modules.
  The tag has only a **module** parameter. 
  The value of the parameter is the name of a module this module depends on.
 * **uselib** describes module dependency on external libraries.
  The tag has only a **lib** parameter. 
  The value of the parameter is the name of a library this module depends on.

Note, dependencies are not transitive. 
If a module M1 depends on a module M2 and the M2 module depends on module M3 and library L1,
the module M1 has no automatic dependency neither on module M3 nor on library L1.
If the M1 module requires this dependencies, they must be explicitly described.
   
#### Module Directories
Each module may have the following, optional, directories:

 * **src** - contains production Java source files.
 * **tests** - contains Java sources with tests and supplementary classes.
   * The build compiles these sources, but does not include them in distributable JARs.
   * Test classes must end with "Test" or "Tests" suffix.
 * **rc** - contains production resources. Build copies all these files to the destination JAR as is and preserves packages.
 * **tests.rc** - contains test resources. These resources are available during execution of tests, but are not included in distributable JARs.
 
### Libraries

A library is one or more external JAR files.
The [meta.xml](meta.xml) file includes the **lib** tag for each library.
The **name** parameter of the library defines the library's name, which is used to refer to this library.

A **lib** tag has one or more **jar** child tags.
Each **jar** tag has one parameter, **jar**, which contains a path to a single JAR file.
The path is relative to the [lib](..\lib) directory.

### Product Description

The **product** tag describes the layout of a distribution.

#### JARs Built from Sources

The [meta.xml](meta.xml) file describes distributable JARs with **distjar** tags.
The tag has a single **jar** parameter, which is the name of the JAR file (it does not include a path).

##### Child Tags
 * **place** - location of the JAR in the distribution. There must be only one such child.
   The only parameter is **dir**: the directory where to place the JAR.
 * **include** - includes a module into the JAR. One child tag for one module to include.
   The only parameter is **module**: the name of a module to include.
 * **manifest** - defines META-INF/MANIFEST.MF file content.
   The **attribute** child tag instructs the build to add one manifest attribute.
   The **name** parameter is the name of the attribute.
   The **value** parameter is the value of the attribute. 

#### Redistributing Libraries

The build copies libraries to the **lib** directory of the distribution.
The build includes only libraries those explicitly mentioned with the **distlib** tag (regardless of declared module dependencies).
Each **distlib** tag has the only parameter **lib** which refers a library by its name.
The build copies all library JAR files.             