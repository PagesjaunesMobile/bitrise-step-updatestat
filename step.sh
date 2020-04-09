#!/bin/bash
set -ex

THIS_SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

#cd "${THIS_SCRIPTDIR}"

mvn -q install:install-file -Dfile=$THIS_SCRIPTDIR/maven/kraph-v.0.6.0.jar -DpomFile=$THIS_SCRIPTDIR/maven/pom-default.xml                                        
kscript $THIS_SCRIPTDIR/step.kts
