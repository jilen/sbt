import sbt._

import java.io.File

class XSbt(info: ProjectInfo) extends ParentProject(info) with NoCrossPaths
{
		/* Subproject declarations*/

	val launchInterfaceSub = project(launchPath / "interface", "Launcher Interface", new LaunchInterfaceProject(_))
	val launchSub = project(launchPath, "Launcher", new LaunchProject(_), launchInterfaceSub)

	val interfaceSub = project("interface", "Interface", new InterfaceProject(_))
	val apiSub = baseProject(compilePath / "api", "API", interfaceSub)

	// util
	val controlSub = baseProject(utilPath / "control", "Control")
	val collectionSub = testedBase(utilPath / "collection", "Collections")
	val ioSub = testedBase(utilPath / "io", "IO", controlSub)
	val classpathSub = baseProject(utilPath / "classpath", "Classpath", launchInterfaceSub, ioSub)
	val completeSub = project(utilPath / "complete", "Completion", new InputProject(_), controlSub, ioSub)
	val logSub = project(utilPath / "log", "Logging", new LogProject(_), interfaceSub)
	val classfileSub = testedBase(utilPath / "classfile", "Classfile", ioSub, interfaceSub, logSub)
	val datatypeSub = baseProject(utilPath /"datatype", "Datatype Generator", ioSub)
	val processSub = project(utilPath / "process", "Process", new Base(_) with TestWithIO, ioSub, logSub)
	val envSub= baseProject(utilPath / "env", "Properties", ioSub, logSub, classpathSub)

	// intermediate-level modules
	val ivySub = project("ivy", "Ivy", new IvyProject(_), interfaceSub, launchInterfaceSub, logSub)
	val testingSub = project("testing", "Testing", new TestingProject(_), ioSub, classpathSub, logSub)
	val taskSub = testedBase(tasksPath, "Tasks", controlSub, collectionSub)
	val cacheSub = project(cachePath, "Cache", new CacheProject(_), ioSub, collectionSub)
	val webappSub = project("web", "Web App", new WebAppProject(_), ioSub, logSub, classpathSub, controlSub)
	val runSub = baseProject("run", "Run", ioSub, logSub, classpathSub, processSub)

	// compilation/discovery related modules
	val compileInterfaceSub = project(compilePath / "interface", "Compiler Interface", new CompilerInterfaceProject(_), interfaceSub)
	val compileIncrementalSub = testedBase(compilePath / "inc", "Incremental Compiler", collectionSub, apiSub, ioSub)
	val discoverySub = testedBase(compilePath / "discover", "Discovery", compileIncrementalSub, apiSub)
	val compilePersistSub = project(compilePath / "persist", "Persist", new PersistProject(_), compileIncrementalSub, apiSub)
	val compilerSub = project(compilePath, "Compile", new CompileProject(_),
		launchInterfaceSub, interfaceSub, ivySub, ioSub, classpathSub, compileInterfaceSub, logSub)

	val buildSub = baseProject("main" / "build", "Project Builder",
		classfileSub, classpathSub, compilePersistSub, compilerSub, compileIncrementalSub, interfaceSub, ivySub, launchInterfaceSub, logSub, discoverySub, processSub)

	val stdTaskSub = testedBase(tasksPath / "standard", "Task System", taskSub, collectionSub, logSub, ioSub, processSub)
	val altCompilerSub = project("main", "Alternate Compiler Test", (i: ProjectInfo) => new Base(i) {  override def normalizedName = "sbt" }, // temporary
		buildSub, compileIncrementalSub, compilerSub, completeSub, discoverySub, ioSub, logSub, processSub, taskSub, stdTaskSub)

	/** following modules are not updated for 2.8 or 0.9 */
	/*val testSub = project("scripted", "Test", new TestProject(_), ioSub)

	val trackingSub = baseProject(cachePath / "tracking", "Tracking", cacheSub)

	val sbtSub = project(sbtPath, "Simple Build Tool", new SbtProject(_) {},
		compilerSub, launchInterfaceSub, testingSub, cacheSub, taskSub)

	val installerSub = project(sbtPath / "install", "Installer", new InstallerProject(_) {}, sbtSub)

	lazy val dist = task { None } dependsOn(launchSub.proguard, sbtSub.publishLocal, installerSub.publishLocal)*/

	def baseProject(path: Path, name: String, deps: Project*) = project(path, name, new Base(_), deps : _*)
	def testedBase(path: Path, name: String, deps: Project*) = project(path, name, new TestedBase(_), deps : _*)
	
		/* Multi-subproject paths */
	def sbtPath = path("sbt")
	def cachePath = path("cache")
	def tasksPath = path("tasks")
	def launchPath = path("launch")
	def utilPath = path("util")
	def compilePath = path("compile")

	def compilerInterfaceClasspath = compileInterfaceSub.projectClasspath(Configurations.Test)

	//run in parallel
	override def parallelExecution = true

	def jlineDep = "jline" % "jline" % "0.9.94" intransitive()

	// publish locally when on repository server
	override def managedStyle = ManagedStyle.Ivy
	val publishTo = Resolver.file("test-repo", new File("/var/dbwww/repo/"))

		/* Subproject configurations*/
	class LaunchProject(info: ProjectInfo) extends Base(info) with TestWithIO with TestDependencies with ProguardLaunch with NoCrossPaths
	{
		val jline = jlineDep
		val ivy = "org.apache.ivy" % "ivy" % "2.1.0"
		override def deliverProjectDependencies = Nil

		// defines the package that proguard operates on
		def rawJarPath = jarPath
		def rawPackage = `package`
		override def packagePaths = super.packagePaths +++ launchInterfaceSub.packagePaths

		// configure testing
		override def testClasspath = super.testClasspath +++ interfaceSub.compileClasspath +++ interfaceSub.mainResourcesPath
		override def testCompileAction = super.testCompileAction dependsOn(interfaceSub.publishLocal, testSamples.publishLocal)

		// used to test the retrieving and loading of an application: sample app is packaged and published to the local repository
		lazy val testSamples = project("test-sample", "Launch Test", new TestSamples(_), interfaceSub, launchInterfaceSub)
		class TestSamples(info: ProjectInfo) extends Base(info) with NoCrossPaths with NoRemotePublish {
			override def deliverProjectDependencies = Nil
		}
	}
	class InputProject(info: ProjectInfo) extends TestedBase(info)
	{
		val jline = jlineDep
	}
	class WebAppProject(info: ProjectInfo) extends Base(info)
	{
		val jetty = "org.mortbay.jetty" % "jetty" % "6.1.14" % "optional"
		val jettyplus = "org.mortbay.jetty" % "jetty-plus" % "6.1.14" % "optional"

		val jetty7server = "org.eclipse.jetty" % "jetty-server" % "7.0.1.v20091125" % "optional"
		val jetty7webapp = "org.eclipse.jetty" % "jetty-webapp" % "7.0.1.v20091125" % "optional"
		val jetty7plus = "org.eclipse.jetty" % "jetty-plus" % "7.0.1.v20091125" % "optional"

		val optional = Configurations.Optional

		/* For generating JettyRun for Jetty 6 and 7.  The only difference is the imports, but the file has to be compiled against each set of imports. */
		override def compileAction = super.compileAction dependsOn (generateJettyRun6, generateJettyRun7)
		def jettySrcDir = info.projectPath
		def jettyTemplate = jettySrcDir / "LazyJettyRun.scala.templ"

		lazy val generateJettyRun6 = generateJettyRunN("6")
		lazy val generateJettyRun7 = generateJettyRunN("7")

		def generateJettyRunN(n: String) =
			generateJettyRun(jettyTemplate, jettySrcDir / ("LazyJettyRun" + n + ".scala"), n, jettySrcDir / ("jetty" + n + ".imports"))

		def generateJettyRun(in: Path, out: Path, version: String, importsPath: Path) =
			task
			{
				(for(template <- FileUtilities.readString(in asFile, log).right; imports <- FileUtilities.readString(importsPath asFile, log).right) yield
					FileUtilities.write(out asFile, processJettyTemplate(template, version, imports), log).toLeft(()) ).left.toOption
			}
		def processJettyTemplate(template: String, version: String, imports: String): String =
			template.replaceAll("""\Q${jetty.version}\E""", version).replaceAll("""\Q${jetty.imports}\E""", imports)
	}
	trait TestDependencies extends Project
	{
		val sc = "org.scala-tools.testing" %% "scalacheck" % "1.7" % "test"
		val sp = "org.scala-tools.testing" %% "specs" % "1.6.5" % "test"
	}
	class LogProject(info: ProjectInfo) extends Base(info) with TestDependencies
	{
		val opt = Configurations.Optional
		val jline = jlineDep % "optional"
	}
	class CacheProject(info: ProjectInfo) extends Base(info) with SBinaryDep
	class PersistProject(info: ProjectInfo) extends Base(info) with SBinaryDep
	trait SBinaryDep extends BasicManagedProject
	{
		// these compilation options are useful for debugging caches and task composition
		//override def compileOptions = super.compileOptions ++ List(Unchecked,ExplainTypes, CompileOption("-Xlog-implicits"))
		val sbinary = "org.scala-tools.sbinary" %% "sbinary" % "0.3.1"
	}
	class Base(info: ProjectInfo) extends DefaultProject(info) with ManagedBase with Component with Licensed
	{
		override def scratch = true
		override def consoleClasspath = testClasspath
		override def compileOptions = super.compileOptions ++ compileOptions("-Xelide-below", "0")
	}
	class TestedBase(info: ProjectInfo) extends Base(info) with TestDependencies
	trait Licensed extends BasicScalaProject
	{
		def notice = path("NOTICE")
		abstract override def mainResources = super.mainResources +++ notice +++ Path.lazyPathFinder( extractLicenses )
		lazy val seeRegex = """\(see (.*?)\)""".r
		def licensePath(str: String): Path = { val path = Path.fromString(XSbt.this.info.projectPath, str); if(path.exists) path else error("Referenced license '" + str + "' not found at " + path) }
		def seePaths(noticeString: String): List[Path] = seeRegex.findAllIn(noticeString).matchData.map(d => licensePath(d.group(1))).toList
		def extractLicenses = if(!notice.exists) Nil else FileUtilities.readString(notice asFile, log).fold(_ => { log.warn("Could not read NOTICE"); Nil} , seePaths _)
	}
	class TestingProject(info: ProjectInfo) extends Base(info)
	{
		val testInterface = "org.scala-tools.testing" % "test-interface" % "0.5"
	}
	class CompileProject(info: ProjectInfo) extends Base(info) with TestWithLog with TestWithLaunch
	{
		override def testCompileAction = super.testCompileAction dependsOn(compileInterfaceSub.`package`, interfaceSub.`package`)
		override def testClasspath = super.testClasspath +++ compileInterfaceSub.packageSrcJar +++ interfaceSub.jarPath --- compilerInterfaceClasspath --- interfaceSub.mainCompilePath
	}
	class IvyProject(info: ProjectInfo) extends Base(info) with TestWithIO with TestWithLog with TestWithLaunch
	{
		val ivy = "org.apache.ivy" % "ivy" % "2.1.0"
	}
	abstract class BaseInterfaceProject(info: ProjectInfo) extends DefaultProject(info) with ManagedBase with TestWithLog with Component with JavaProject
	class InterfaceProject(info: ProjectInfo) extends BaseInterfaceProject(info)
	{
		override def componentID: Option[String] = Some("xsbti")
		override def packageAction = super.packageAction dependsOn generateVersions
		def versionPropertiesPath = mainResourcesPath / "xsbt.version.properties"
		lazy val generateVersions = task {
			import java.util.{Date, TimeZone}
			val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
			formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
			val timestamp = formatter.format(new Date)
			val content = "version=" + version + "\ntimestamp=" + timestamp
			log.info("Writing version information to " + versionPropertiesPath + " :\n" + content)
			FileUtilities.write(versionPropertiesPath.asFile, content, log)
		}

		override def watchPaths = super.watchPaths +++ apiDefinitionPaths --- sources(generatedBasePath)
		override def mainSourceRoots = super.mainSourceRoots +++ (generatedBasePath ##)
		def srcManagedPath = path("src_managed")
		def generatedBasePath = srcManagedPath / "main" / "java"
		/** Files that define the datatypes.*/
		def apiDefinitionPaths: PathFinder = "definition"
		/** Delete up the generated sources*/
		lazy val cleanManagedSrc = cleanTask(srcManagedPath)
		override def cleanAction = super.cleanAction dependsOn(cleanManagedSrc)
		/** Runs the generator compiled by 'compile', putting the classes in src_managed and processing the definitions 'apiDefinitions'. */
		lazy val generateSource = generateSourceAction dependsOn(cleanManagedSrc, datatypeSub.compile)
		def generateSourceTask(immutable: Boolean, pkg: String, apiDefinitions: PathFinder): Task =
		{
			val m = if(immutable) "immutable" else "mutable"
			generateSourceTask(m :: pkg :: generatedBasePath.absolutePath :: apiDefinitions.get.toList.map(_.absolutePath))
		}
		def generateSourceTask(args: List[String]): Task =
			runTask(datatypeSub.getMainClass(true), datatypeSub.runClasspath, args)
		def generateSourceAction =
			//generateSourceTask(false, "xsbti.api", "definition" +++ "type") &&
			generateSourceTask(true, "xsbti.api", "other" +++ "definition" +++ "type")
		/** compiles the generated sources */
		override def compileAction = super.compileAction dependsOn(generateSource)
	}
	class LaunchInterfaceProject(info: ProjectInfo) extends BaseInterfaceProject(info)
	{
		override def componentID = None
	}
	class TestProject(info: ProjectInfo) extends Base(info)
	{
		val process = "org.scala-tools.sbt" % "process" % "0.1"
	}
	class CompilerInterfaceProject(info: ProjectInfo) extends Base(info) with PrecompiledInterface with NoCrossPaths with TestWithIO with TestWithLog
	{ cip => 
		//val jline = jlineDep artifacts(Artifact("jline", Map("e:component" -> srcID)))
		// necessary because jline is not distributed with 2.8 and we will get a compile error 
		// sbt should work with the above inline declaration, but it doesn't, so the inline Ivy version is used for now.
		override def ivyXML =
			( <publications />
			<dependencies>
				<dependency org="jline" name="jline" rev="0.9.94" transitive="false">
					<artifact name="jline" type="jar" e:component={srcID}/>
				</dependency>
			</dependencies> )

		def xTestClasspath =  projectClasspath(Configurations.Test)

		def srcID = "compiler-interface-src"
		lazy val srcArtifact = Artifact(srcID) extra("e:component" -> srcID)
		override def packageSrcJar = mkJarPath(srcID)
		lazy val pkgSrc = packageSrc // call it something else because we don't need dependencies to run package-src
		override def packageAction = super.packageAction dependsOn(pkgSrc)
		
		// sub projects for each version of Scala to precompile against other than the one sbt is built against
		// each sub project here will add ~100k to the download
		//lazy val precompiled28 = precompiledSub("2.8.0")
		lazy val precompiled27 = precompiledSub("2.7.7")

		def precompiledSub(v: String) = 
			project(info.projectPath, "Precompiled " + v, new Precompiled(v)(_), cip.info.dependencies.toSeq : _* /*doesn't include subprojects of cip*/ )

		/** A project that compiles the compiler interface against the Scala version 'sv'.
		* This is done for selected Scala versions (generally, popular ones) so that it doesn't need to be done at runtime. */
		class Precompiled(sv: String)(info: ProjectInfo) extends Base(info) with PrecompiledInterface with NoUpdate {
			/** force the Scala version in order to precompile the compiler interface for different Scala versions*/
			override def buildScalaVersion = sv

			/** Get compilation classpath from parent.  Scala dependencies are added on top of this and this
			* subproject does not depend on any Scala subprojects, so mixing versions is not a problem. */
			override def compileClasspath = cip.compileClasspath --- cip.mainUnmanagedClasspath +++ mainUnmanagedClasspath

			override def compileOptions = Nil
			// these ensure that the classes compiled against other versions of Scala are not exported (for compilation/testing/...)
			override def projectClasspath(config: Configuration) = Path.emptyPathFinder
		}
	}
	trait TestWithIO extends TestWith {
		override def testWithTestClasspath = super.testWithTestClasspath ++ Seq(ioSub)
	}
	trait TestWithLaunch extends TestWith {
		override def testWithTestClasspath = super.testWithTestClasspath ++ Seq(launchSub)
	}
	trait TestWithLog extends TestWith {
		override def testWithCompileClasspath = super.testWithCompileClasspath ++ Seq(logSub)
	}
	trait TestWith extends BasicScalaProject
	{
		def testWithCompileClasspath: Seq[BasicScalaProject] = Nil
		def testWithTestClasspath: Seq[BasicScalaProject] = Nil
		override def testCompileAction = super.testCompileAction dependsOn((testWithTestClasspath.map(_.testCompile) ++ testWithCompileClasspath.map(_.compile)) : _*)
		override def testClasspath = (super.testClasspath /: (testWithTestClasspath.map(_.testClasspath) ++  testWithCompileClasspath.map(_.compileClasspath) ))(_ +++ _)
	}
}
