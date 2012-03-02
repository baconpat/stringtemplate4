/*
 [The "BSD license"]
 Copyright (c) 2009 Terence Parr
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.stringtemplate.v4.test;

import org.antlr.runtime.*;
import org.junit.Before;
import org.stringtemplate.v4.*;
import org.stringtemplate.v4.compiler.Compiler;
import org.stringtemplate.v4.compiler.*;
import org.stringtemplate.v4.misc.Misc;

import java.io.*;
import java.util.*;

import static org.junit.Assert.assertEquals;


public abstract class BaseTest {
	public static final String pathSep = System.getProperty("path.separator");
    public static final String tmpdir = System.getProperty("java.io.tmpdir");
    public static final String newline = Misc.newline;

	/**
	 * When runnning from Maven, the junit tests are run via the surefire plugin. It sets the
	 * classpath for the test environment into the following property. We need to pick this up
	 * for the junit tests that are going to generate and try to run code.
	 */
	public static final String SUREFIRE_CLASSPATH =
		System.getProperty("surefire.test.class.path", "");

	public static final String CLASSPATH = System.getProperty("java.class.path") +
										   (SUREFIRE_CLASSPATH.equals("") ?
											   "" :
											   pathSep + SUREFIRE_CLASSPATH);

	public static class StreamVacuum implements Runnable {
		StringBuffer buf = new StringBuffer();
		BufferedReader in;
		Thread sucker;
		public StreamVacuum(InputStream in) {
			this.in = new BufferedReader( new InputStreamReader(in) );
		}
		public void start() {
			sucker = new Thread(this);
			sucker.start();
		}
		public void run() {
			try {
				String line = in.readLine();
				while (line!=null) {
					buf.append(line);
					buf.append('\n');
					line = in.readLine();
				}
			}
			catch (IOException ioe) {
				System.err.println("can't read output from process");
			}
		}
		/** wait for the thread to finish */
		public void join() throws InterruptedException {
			sucker.join();
		}
		public String toString() {
			return buf.toString();
		}
	}

    @Before
    public void setUp() {
        STGroup.defaultGroup = new STGroup();
        Compiler.subtemplateCount = 0;
    }

    /**
     * Creates a file "Test.java" in the directory dirName containing a main
     * method with content starting as given by main.
     * <p/>
     * The value of a variable 'result' defined in 'main' is written to
     * System.out, followed by a newline character.
     * <p/>
     * The final newline character is just the '\n' character, not the
     * system specific line separator ({@link #newline}).
     *
     * @param main
     * @param dirName
     */
	public void writeTestFile(String main, String dirName) {
		ST outputFileST = new ST(
			"import org.antlr.runtime.*;\n" +
			"import org.stringtemplate.v4.*;\n" +
			"import org.antlr.runtime.tree.*;\n" +
			"import java.io.*;\n" +
			"import java.net.*;\n" +
			"\n" +
			"public class Test {\n" +
			"    public static void main(String[] args) throws Exception {\n" +
			"        <code>\n"+
			"        System.out.println(result);\n"+
			"    }\n" +
			"}"
			);
		outputFileST.add("code", main);
		writeFile(dirName, "Test.java", outputFileST.render());
	}

	public String java(String mainClassName, String extraCLASSPATH, String workingDirName) {
		String classpathOption = "-classpath";

		String path = "."+pathSep+CLASSPATH;
		if ( extraCLASSPATH!=null ) path = "."+pathSep+extraCLASSPATH+pathSep+CLASSPATH;

		String[] args = new String[] {
					"java",
					classpathOption, path,
					mainClassName
		};
		System.out.println("executing: "+Arrays.toString(args));
		return exec(args, null, workingDirName);
	}

	public void jar(String fileName, String[] files, String workingDirName) {
		String[] cmd = {
			"jar", "cf", fileName, "Test.class"
		};
		// SO SAD FOR YOU JAVA!!!!
		List<String> list = new ArrayList<String>();
		list.addAll(Arrays.asList(cmd));
		list.addAll(Arrays.asList(files));
		String[] a = new String[list.size()];
		list.toArray(a);
		exec(a, null, workingDirName); // create jar
	}

	public void compile(String fileName, String workingDirName) {
		String classpathOption = "-classpath";

		String[] args = new String[] {
					"javac",
					classpathOption, "."+pathSep+CLASSPATH,
					fileName
		};
		exec(args, null, workingDirName);
	}

	public String exec(String[] args, String[] envp, String workingDirName) {
		String cmdLine = Arrays.toString(args);
		File workingDir = new File(workingDirName);
		try {
			Process process =
				Runtime.getRuntime().exec(args, envp, workingDir);
			StreamVacuum stdout = new StreamVacuum(process.getInputStream());
			StreamVacuum stderr = new StreamVacuum(process.getErrorStream());
			stdout.start();
			stderr.start();
			process.waitFor();
            stdout.join();
            stderr.join();
			if ( stdout.toString().length()>0 ) {
				return stdout.toString();
			}
			if ( stderr.toString().length()>0 ) {
				System.err.println("compile stderr from: "+cmdLine);
				System.err.println(stderr);
			}
			int ret = process.exitValue();
			if ( ret!=0 ) System.err.println("failed");
		}
		catch (Exception e) {
			System.err.println("can't exec compilation");
			e.printStackTrace(System.err);
		}
		return null;
	}

	public static void writeFile(String dir, String fileName, String content) {
		try {
			File f = new File(dir, fileName);
            if ( !f.getParentFile().exists() ) f.getParentFile().mkdirs();
			FileWriter w = new FileWriter(f);
			BufferedWriter bw = new BufferedWriter(w);
			bw.write(content);
			bw.close();
			w.close();
		}
		catch (IOException ioe) {
			System.err.println("can't write file");
			ioe.printStackTrace(System.err);
		}
	}

    public void checkTokens(String template, String expected) {
        checkTokens(template, expected, '<', '>');
    }


    public void checkTokens(String template, String expected,
                            char delimiterStartChar, char delimiterStopChar)
    {
        STLexer lexer =
            new STLexer(STGroup.DEFAULT_ERR_MGR,
						new ANTLRStringStream(template),
						null,
						delimiterStartChar,
						delimiterStopChar);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		StringBuilder buf = new StringBuilder();
		buf.append("[");
		int i = 1;
        Token t = tokens.LT(i);
        while ( t.getType()!=Token.EOF ) {
            if ( i>1 ) buf.append(", ");
            buf.append(t);
            i++;
            t = tokens.LT(i);
        }
        buf.append("]");
        String result = buf.toString();
        assertEquals(expected, result);
    }

    public static class User {
        public int id;
        public String name;
        public User(int id, String name) { this.id = id; this.name = name; }
		public boolean isManager() { return true; }
		public boolean hasParkingSpot() { return true; }
        public String getName() { return name; }
    }

    public static class HashableUser extends User {
        public HashableUser(int id, String name) { super(id, name); }
        public int hashCode() {
            return id;
        }

        public boolean equals(Object o) {
            if ( o instanceof HashableUser ) {
                HashableUser hu = (HashableUser)o;
                return this.id == hu.id && this.name.equals(hu.name);
            }
            return false;
        }
	}

    public static String getRandomDir() {
        String randomDir = tmpdir+"dir"+String.valueOf((int)(Math.random()*100000));
        File f = new File(randomDir);
        f.mkdirs();
        return randomDir;
    }

	/**
	 * Removes the specified file or directory, and all subdirectories.
	 *
	 * Nothing if the file does not exists.
	 *
	 * @param file
	 */
	public static void deleteFile(File file) {
		if (file.exists()) {
			if (file.isDirectory()) {
				File[] dir = file.listFiles();
				for (int i = 0; i < dir.length; i++) {
					deleteFile(dir[i]);
				}
			}
			if (!file.delete()) {
				throw new RuntimeException("Error when deleting file "
						+ file.getAbsolutePath());
			}
		}
	}
	/**
	 * see {@link #deleteFile(File)}
	 *
	 * @param file
	 */
	public static void deleteFile(String file) {
		deleteFile(new File(file));
	}

}
