#!/bin/bash



all="opennlp jtok freetts sgt weka mwdumper"

############## individual installer functions #############


installOpennlp() {
  echo "Installing opennlp from source..."
  cd $MARYBASE/tmp
  svn checkout https://svn.apache.org/repos/asf/incubator/opennlp/tags/opennlp-1.5.1-incubating-rc7 opennlp
  cd opennlp/opennlp
  mvn install -Dmaven.test.skip=true
}



installFreetts() {
  echo "Installing freetts jar files..."
  cd $MARYBASE
  mvn install:install-file -DgroupId=com.sun.speech.freetts -DartifactId=freetts -Dversion=1.0 -Dpackaging=jar -Dfile=dependencies/freetts.jar
  mvn install:install-file -DgroupId=com.sun.speech.freetts -DartifactId=freetts-de -Dversion=1.0 -Dpackaging=jar -Dfile=dependencies/freetts-de.jar
  mvn install:install-file -DgroupId=com.sun.speech.freetts -DartifactId=freetts-en_us -Dversion=1.0 -Dpackaging=jar -Dfile=dependencies/freetts-en_us.jar

}



installJtok() {
  echo "Installing Jtok from source..."
  cd $MARYBASE/tmp
  svn checkout https://heartofgold.opendfki.de/repos/tags/jtok/release_1.9 jtok
  cd jtok
  patch -p0 < ../../dependencies/jtok1.9-capitalization-fix.diff
  mvn install
}



installSgt() {
  echo "Installing sgt jar file..."
  cd $MARYBASE
  mvn install:install-file -DgroupId=gov.noaa.pmel.sgt -DartifactId=sgt -Dversion=3.0 -Dpackaging=jar -Dfile=dependencies/sgt_v30.jar -Dsources=dependencies/sgt_src_v30.jar
}



installWeka() {
  echo "Installing weka jar file..."
  cd $MARYBASE
  mvn install:install-file -DgroupId=nz.ac.waikato.cs.ml -DartifactId=weka -Dversion=3.7.3 -Dpackaging=jar -Dfile=dependencies/weka-3.7.3.jar -Dsources=dependencies/weka-3.7.3-src.jar
}

installMwdumper() {
  echo "Installing MWdumper from source..."
  cd $MARYBASE/tmp
  svn checkout -r 87118 http://svn.wikimedia.org/svnroot/mediawiki/trunk/mwdumper
  cd mwdumper
  mvn install
}



################# main program ###################
MARYBASE="`dirname "$0"`"

if [ $# -gt 0 ] ; then
  deps=$*
else 
  deps=$all
fi

echo "Will try to install the following dependencies: $deps"

for dep in $deps ; do
  case "$dep" in 
    opennlp) installOpennlp ;;
    freetts) installFreetts ;;
    jtok) installJtok ;;
    sgt) installSgt ;;
    weka) installWeka ;;
    mwdumper) installMwdumper ;;
    *) echo "Ignoring unknown module '$dep'" ;;
  esac
done


