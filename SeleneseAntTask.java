import java.io.*;
import java.net.*;
import java.util.regex.*;

import org.apache.tools.ant.*;
import org.openqa.selenium.server.*;
import org.openqa.selenium.server.htmlrunner.*;

/**
 * An Ant task to run a suite of HTML Selenese tests.
 *
 * <p>Use it like this (for example):<blockquote><code>
 * &lt;taskdef&nbsp;resource="selenium-ant.properties"&gt;<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;classpath&gt;<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&lt;pathelement&nbsp;location="selenium-server.jar"/&gt;<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;&lt;/classpath&gt;<br/>
 * &lt;/taskdef&gt;<br/>
 * &lt;selenese&nbsp;<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;suiteDirectory="c:\absolute\path\to\my\suiteDirectory"<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;browser="*firefox"<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;results="c:\absolute\path\to\my\results.html"<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;multiWindow="true"<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;timeoutInSeconds="900"<br/>
 * &nbsp;&nbsp;&nbsp;&nbsp;startURL="http://www.google.com"&nbsp;/&gt;</code></blockquote>
 *
 * <h3>Parameters</h3>
 * <table border="1" cellpadding="2" cellspacing="0">
 *   <tr>
 *     <td valign="top"><b>Attribute</b></td>
 *
 *     <td valign="top"><b>Description</b></td>
 *     <td align="center" valign="top"><b>Required</b></td>
 *   </tr>
 *   <tr>
 *     <td valign="top">suiteDirectory</td>
 *
 *     <td valign="top">The directory containing all of the test suite files to run.</td>
 *     <td align="center" valign="top">Yes</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">browser</td>
 *
 *     <td valign="top">The browser name to run; must be one of the standard valid browser names (and must start with a *), e.g. *firefox, *iexplore, *custom.</td>
 *     <td align="center" valign="top">Yes</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">startURL</td>
 *
 *     <td valign="top">The base URL on which the tests will be run, e.g. http://www.google.com.  Note that only the hostname part of this URL will really be used.</td>
 *     <td align="center" valign="top">Yes</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">results</td>
 *     <td valign="top">The file to which we'll write out our test results.</td>
 *     <td align="center" valign="top">No, defaults to "results-&#064;{browser}-&#064;{options}-&#064;{suite}" where &#064;{options} may include "-multiWindow" and/or "-slowResources"</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">outputDir</td>
 *     <td valign="top">The directory in which we'll create the test results file.  Ignored if "results" is absolute.</td>
 *     <td align="center" valign="top">No, defaults to "."</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">haltonfailure</td>
 *     <td valign="top">Stop the build process if a test fails.</td>
 *     <td align="center" valign="top">No, defaults to true</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">failureproperty</td>
 *     <td valign="top">The name of a property to set in the event of a failure.</td>
 *     <td align="center" valign="top">No</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">port</td>
 *     <td valign="top">The port on which we'll run the Selenium Server</td>
 *     <td align="center" valign="top">No, defaults to 4444</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">timeoutInSeconds</td>
 *     <td valign="top">Amount of time to wait before we just kill the browser</td>
 *     <td align="center" valign="top">No, defaults to 30 minutes (1800 seconds)</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">javaScriptCoreDir</td>
 *     <td valign="top">The directory containing Selenium Core, if you want to use your own instead of the baked-in version.  You can also specify the property "selenium.javascript.dir" to override this setting</td>
 *     <td align="center" valign="top">No</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">multiWindow</td>
 *     <td valign="top">true if the application under test should run in its own window, false if the AUT will run in an embedded iframe</td>
 *     <td align="center" valign="top">No, defaults to false</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">slowResources</td>
 *     <td valign="top">A debugging tool that slows down the Selenium Server.  (Selenium developers only)</td>
 *     <td align="center" valign="top">No, defaults to false</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">firefoxProfileTemplate</td>
 *     <td valign="top">The name of the firefox template that Selenium should use</td>
 *     <td align="center" valign="top">No</td>
 *   </tr>
 *   <tr>
 *     <td valign="top">userExtensions</td>
 *     <td valign="top">The file containing Selenium user-extensions</td>
 *     <td align="center" valign="top">No</td>
 *   </tr>
 * </table>
 *
 * TODO: more options! fork=true? singleTest?
 * @author Dan Fabulich
 *
 */
public class SeleneseAntTask extends Task {

	private static final String SELENIUM_JAVASCRIPT_DIR = "selenium.javascript.dir";
    private static final Pattern browserPattern = Pattern.compile("\\*(\\w+)");
    private static final String PASSED = "PASSED";

    private int timeoutInSeconds = RemoteControlConfiguration.DEFAULT_TIMEOUT_IN_SECONDS;
    private int port = RemoteControlConfiguration.DEFAULT_PORT;
    private File results, outputDir, suiteDirectory, userExtensions, firefoxProfileTemplate;
    private boolean slowResources, multiWindow;
    private boolean haltOnFailure=true;
    private String browser, startURL;
    private String failureProperty;

    public SeleneseAntTask() {
		super();
	}

	@Override
	public void execute() {
        checkEnvironment();
		SeleniumServer server = null;
		try {
            RemoteControlConfiguration configuration = new RemoteControlConfiguration();
            configuration.setFirefoxProfileTemplate(firefoxProfileTemplate);
            configuration.setPort(port);
            configuration.setUserExtensions(userExtensions);
            configuration.setTrustAllSSLCertificates(true);            
            server = new SeleniumServer(slowResources, configuration);
			server.start();
            runTestSuites(server);
		} catch (Exception e) {
			throw new BuildException(e);
		} finally {
            if (server != null) server.stop();
        }
	}

    private void checkEnvironment() {
        checkForNulls();
        checkForJavaScriptCoreDir();
    }

    private void runTestSuites(SeleniumServer server) throws IOException {
        log("Test Suite Directory: " + suiteDirectory.getAbsolutePath());
        for (File suite : suiteDirectory.listFiles()) {
            HTMLLauncher htmlLauncher = new HTMLLauncher(server);
            if(suite.getName().startsWith("Suite")){
                runTestSuite(suite, htmlLauncher);
            }
        }
    }

    private void runTestSuite(File suite, HTMLLauncher launcher) throws IOException {
        log("Running Test Suite: " + suite.getName());
        File suiteResultsFile = createResultsFile(suite.getName());
        String result = launcher.runHTMLSuite(browser, startURL, suite, suiteResultsFile, timeoutInSeconds, multiWindow);
        checkResult(result, suiteResultsFile);
    }

    private void checkResult(String result, File suiteResultsFile) {
        if (!PASSED.equals(result)) {
            String errorMessage = "Tests failed, see result file for details: " + suiteResultsFile.getAbsolutePath();
            if(haltOnFailure) {
                throw new BuildException(errorMessage);
            }
            log(errorMessage, Project.MSG_ERR);
            if (failureProperty != null) {
                getProject().setProperty(failureProperty, "true");
            }
        }
    }

    private void checkForJavaScriptCoreDir() {
		// Ant puts its properties in the project, not in the system
		if (System.getProperties().containsKey(SELENIUM_JAVASCRIPT_DIR)) return;
		String antProperty = getProject().getProperty(SELENIUM_JAVASCRIPT_DIR);
		if (antProperty != null) {
			System.setProperty(SELENIUM_JAVASCRIPT_DIR, antProperty);
		}
	}

	private void checkForNulls() {
		if (browser == null) {
			throw new BuildException("You must specify a browser");
		}
		if (startURL == null) {
			throw new BuildException("You must specify a start URL");
		}
		if (suiteDirectory == null) {
			throw new BuildException("You must specify a suite directory");
		}
		if (outputDir == null) {
		    outputDir = getProject().getBaseDir();
		}
		if (results == null) {
			String options = (multiWindow ? "multiWindow-" : "") + (slowResources ? "slowResources-" : "");
			String name = "results-" + extractUsableBrowserName() + '-' + options + suiteDirectory.getName();
			setResults(new File(name));
		}
	}

	private String extractUsableBrowserName() {
		Matcher m = browserPattern.matcher(browser);
		if (m.find()) {
			return m.group(1);
		}
		throw new BuildException("Couldn't parse browser string "+ browser +"to generate default results file. Please specify a results file.");
	}

	public void setBrowser(String browser) {
		this.browser = browser;
	}

	public void setMultiWindow(boolean multiWindow) {
		this.multiWindow = multiWindow;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setResults(File results) {
	    this.results = results;

	}

    private File createResultsFile(String suiteName) {
        File suiteResultsFile = new File(results + "-" +  suiteName);
        if (!suiteResultsFile.isAbsolute()) {
            suiteResultsFile = new File(outputDir, suiteResultsFile.getPath());
        }
        try {
			suiteResultsFile.createNewFile();
		} catch (IOException e) {
			throw new BuildException("can't write to results file: " + suiteResultsFile.getAbsolutePath(), e);
		}
		if (!suiteResultsFile.canWrite()) throw new BuildException("can't write to results file: " + suiteResultsFile.getAbsolutePath());
		log("Results will go to " + suiteResultsFile.getAbsolutePath());
        return suiteResultsFile;
    }

	public void setSlowResources(boolean slowResources) {
		this.slowResources = slowResources;
	}

	public void setStartURL(URL u) {
		this.startURL = u.toString();
	}

	public void setTimeoutInSeconds(int timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
	}

	public void setJavaScriptCoreDir(File coreDir) {
		if (!coreDir.exists()) throw new BuildException("core dir doesn't exist: " + coreDir.getAbsolutePath());
		System.setProperty(SELENIUM_JAVASCRIPT_DIR, coreDir.getAbsolutePath());
	}


	public void setHaltOnFailure(boolean haltOnFailure) {
		this.haltOnFailure=haltOnFailure;
	}

    public void setFailureProperty(String failureProperty) {
        this.failureProperty = failureProperty;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }


    public void setFirefoxProfileTemplate(File firefoxProfileTemplate) {
        if (!firefoxProfileTemplate.exists()) throw new BuildException("firefoxProfileTemplate doesn't exist: " + firefoxProfileTemplate.getAbsolutePath());
		if (!firefoxProfileTemplate.canRead()) throw new BuildException("can't read firefoxProfileTemplate: " + firefoxProfileTemplate.getAbsolutePath());
        this.firefoxProfileTemplate = firefoxProfileTemplate;
    }

    public void setSuiteDirectory(File suiteDirectory) {
        if (!suiteDirectory.exists()) throw new BuildException("suite directory doesn't exist: " + suiteDirectory.getAbsolutePath());
		if (!suiteDirectory.canRead()) throw new BuildException("can't read suite file: " + suiteDirectory.getAbsolutePath());
        this.suiteDirectory = suiteDirectory;
    }

    public void setUserExtensions(File userExtensions) {
        if (!userExtensions.exists()) throw new BuildException("user extensions file doesn't exist: " + userExtensions.getAbsolutePath());
		if (!userExtensions.canRead()) throw new BuildException("can't read user extensions file: " + userExtensions.getAbsolutePath());
        this.userExtensions = userExtensions;
    }
}
