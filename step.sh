#!/bin/bash
set -ex

THIS_SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

#cd "${THIS_SCRIPTDIR}"

kscript $THIS_SCRIPTDIR/step.kts
