This build is intended for use with Jira Cloud, not Jira Server.

## How to Build

### Prerequisites

In order to build the project you need [Apache Ant](https://ant.apache.org/) version 1.10.7 and [Oracle JDK](https://www.oracle.com/java/) version 8. 

1. Download Apache Ant from [Apache Bin Download]https://ant.apache.org/bindownload.cgi.
  
     The build has been tested with Ant version 1.10.7.

2. Download Oracle JDK 8 from [Java Download Archive](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)

     The build has been tested with version 8u192, though it is likely compatible with any JDK version
     from 8u112 to 8u202.
   
     Note, the build is NOT compatible with:
    
      * Java 9 and later
    
      * Java 8 updates before 8u112
    
      * any version of OpenJDK.   

### Build Steps - ANT

1. Have Apache Ant and Oracle JDK 8 installed on your system.

2. Check out the right branch of the repository.

     See the Branches section below in this file to choose the right one.

     Do NOT build the main branch.

3. Set the environment variables below, either in the shell or platform-wide, then run [build.sh](ant/build.sh) from the
[ant](ant) directory.

     | Variable | Required | Meaning |
     | --- | --- | --- |
     | `ANT_HOME` | yes | Apache Ant install directory (the one holding `bin` and `lib`) |
     | `JDK8_HOME` | yes | Oracle JDK 8 home directory (the one holding `bin` and `lib`) |
     | `BUILD_NUMBER` | no | Overrides the build number. Leave it unset: the build derives one from the git commit count |
     | `ANT_FILE` | no | Build file to run. Defaults to `./build.xml` |

     The build number is shown on the About screen and used in the ZIP name. It is normally
     `git rev-list --count HEAD`, so it needs no configuration and is the same on every checkout of a given
     commit. Set `BUILD_NUMBER` only to override that. When no git count can be established - git is not on
     `PATH`, or the source is an export rather than a repository - the build falls back to `1`.

4. When the build successfully completes, find the built application in the
[build/.dist/jiraclient](/build/.dist/jiraclient) directory, and the ZIPed
application in `build/.dist/jiraclient-NNNN.zip`, where `NNNN` is the build number.

For more details see the [build documentation](ant/BUILD.md).

#### Shell considerations

When building Client for Jira from a shell environment, ensure you use the correct path structure and arguments for your shell. Errors like:

```
build.sh: line <N>: C:/Program Files/Java/jdk1.8.0_192/bin/java: No such file or directory
```

when such paths exist indicate that paths are not resolved correctly for your shell. Note that on a machine with WSL installed, `bash` in PowerShell or Command
Prompt runs **WSL**, not **Git Bash** as is often assumed. WSL requires Unix-style paths (e.g. `/mnt/c/`) whereas Git Bash requires Windows-style paths (e.g. `C:/`).

Pick one of the four setups below. None of them set `BUILD_NUMBER`, since the build derives its own; add it only
to override, and note that `NNNN` stands for that override value where it appears.

#### 1. PowerShell with Git Bash (recommended on Windows)

Use Windows paths. Call the Git Bash executable by its full path. Environment variables set in PowerShell are inherited by Git Bash. Assuming current path is repository root.

```powershell
$env:ANT_HOME  = "C:/Program Files/Apache Ant 1.10.7"
$env:JDK8_HOME = "C:/Program Files/Java/jdk1.8.0_192"

cd ant
& "C:\Program Files\Git\bin\bash.exe" ./build.sh
```

#### 2. PowerShell with WSL bash

Install Oracle JDK 8 and Ant **inside** WSL and use Linux paths throughout.

Note that PowerShell environment variables are not passed into WSL automatically so set them inside the WSL command:

```powershell
wsl bash -c 'export ANT_HOME=/opt/apache-ant-1.10.7; export JDK8_HOME=/usr/lib/jvm/jdk1.8.0_192; cd ant && ./build.sh'
```

#### 3. Windows Command Prompt

Either call Git Bash explicitly:

```bat
set "ANT_HOME=C:/Program Files/Apache Ant 1.10.7"
set "JDK8_HOME=C:/Program Files/Java/jdk1.8.0_192"

cd ant
"C:\Program Files\Git\bin\bash.exe" ./build.sh
```

or skip the script and invoke the Ant launcher directly, which needs no shell:

```bat
cd ant
"C:\Program Files\Java\jdk1.8.0_192\bin\java.exe" -cp "C:\Program Files\Apache Ant 1.10.7\lib\ant-launcher.jar" org.apache.tools.ant.launch.Launcher -f .\build.xml prepareDistribution -Djdk="C:\Program Files\Java\jdk1.8.0_192"
```

#### 4. Linux

Use ordinary Linux paths.

```sh
export ANT_HOME=/opt/apache-ant-1.10.7
export JDK8_HOME=/usr/lib/jvm/jdk1.8.0_192

cd ant
./build.sh
```

Note that OpenJDK is not supported; `JDK8_HOME` must point to an Oracle JDK 8.

#### Compiling without running tests

`build.xml` defines only `clean`, `init`, `generateBuildXml` and `prepareDistribution`.
The per-module targets live in `runGenerated.xml`, which `build.xml` generates. To
check that the code compiles without running the whole test suite:

```sh
./build.sh generateBuildXml init
ANT_FILE=./runGenerated.xml ./build.sh ALL.compile
```

A successful `ALL.compile` means the code compiles.

### Build Steps - Jetbrains IDEA
1. Have Oracle JDK 8 installed on your system.

2. Check out the correct branch of the repository.

     See the Branches section below in this file to choose the correct one.

     Do NOT build the main branch.
	 
3. Install JetBrains IDEA - tested on 2026.2.

4. Install the "Swing GUI Designer" plugin - Tested on 262.8665.176.

5. Open the repository as a project.

6. Create a Run configuration for an Application.
6.1. Set JDK to JAVA 8 as installed above.
6.2. Set module classpath to "Idea.JiraClient".
6.3. Set VM arguments to: "-Xmx512m -Di.a=true -Djiraclient.debug=true -Djira.dump=all -Djiraclient.home=. -Ddebug.components=com/almworks/rc/DebugComponents-JIRAClient3.xml -Ddebug.context=true -Ddebug.allows.kleval=true -Dis.debugging=true -Djiraclient.debug.level=fine". Configure debug arguments as needed.
6.4. Set main method to "com.almworks.launcher.Launcher".
6.5. Set CLI arguments to the path where you want to store the database, e.g. "C:/Repositories/ClientForJira/TestArea/Cloud-temp1".
6.6. Set a working directory, e.g. "C:/Repositories/ClientForJira/TestArea/".

7. Run the application.

## How to Run

To start the built application run the start up script from command-line terminal.
The script is located in:

 * [jiraclient.sh](./build/.dist/jiraclient/bin/jiraclient.sh) for Mac
 
 * [linux_jiraclient.sh](./build/.dist/jiraclient/bin/linux_jiraclient.sh) for Linux
 
 * [jiraclient.bat](./build/.dist/jiraclient/bin/jiraclient.bat) for Windows
 
You can pass `workspace` location as command-line parameter.

Java either must be available on the PATH environment variable or the JAVA_HOME environment variable must be defined and point to the corresponding JRE or JDK.

## Branches

Because of backward-incompatible changes of Jira Cloud REST API, this project has two branches. The `cloud` branch is actively being maintained, the `server` branch has been left as legacy code.

 * `cloud`
 This branch contains the latest Client for Jira - Cloud release compatible with the most recent Jira Cloud REST API. This application is not backward compatible with previous releases.

 * `server`
 This branch contains the latest Client for Jira - Server release (left at version 3.8.4). It is not compatible with Jira Cloud, but this branch is backward-compatible with previous releases of Client for Jira.
 
 
 Copyright 2004–2020 [ALM Works, Inc](https://almworks.com/). This work is licensed under the terms of [GPL v3  license](https://www.gnu.org/licenses/gpl-3.0.html). 
 If you require a different license, please contact [info@almworks.com](info@almworks.com). Modified by Thomas Plaisier, Copyright 2026.