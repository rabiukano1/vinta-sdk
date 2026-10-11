// Autolinking: Android only for now (iOS SDK not released yet).
module.exports = {
  dependency: {
    platforms: {
      android: { packageImportPath: "import org.view2earn.vinta.rn.VintaPackage;", packageInstance: "new VintaPackage()" },
      ios: null,
    },
  },
};
