/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.nativeintegration.filesystem.jdk7

import com.sun.security.auth.module.NTSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class Jdk7SymlinkTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder

    @Requires(TestPrecondition.SYMLINKS)
    def 'on symlink supporting system, it will return true for supported symlink'() {
        expect:
        new Jdk7Symlink().isSymlinkCreationSupported()
    }

    @Requires(TestPrecondition.NO_SYMLINKS)
    def 'on non symlink supporting system, it will return false for supported symlink'() {
        expect:
        !new WindowsJdk7Symlink().isSymlinkCreationSupported()
    }

    @Unroll
    def 'deletes test files after symlink support test with #implementationClass'() {
        expect:
        listSymlinkTestFiles().findAll { !it.delete() }.empty
        implementationClass.newInstance()
        listSymlinkTestFiles().empty

        where:
        implementationClass << [Jdk7Symlink, WindowsJdk7Symlink]
    }

    @Requires(TestPrecondition.SYMLINKS)
    def 'can create and detect symlinks'() {
        def symlink = new Jdk7Symlink()
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        symlink.symlink(new File(testDirectory, 'testFile'), testDirectory.createFile('symFile'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testFile'))

        when:
        symlink.symlink(new File(testDirectory, 'testDir'), testDirectory.createDir('symDir'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testDir'))
    }

    @Requires(TestPrecondition.WINDOWS)
    def 'can detect Windows symbolic links as symbolic links'() {
        def symlink = new WindowsJdk7Symlink()
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        createWindowsSymbolicLink(new File(testDirectory, 'testFile'), testDirectory.createFile('symFile'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testFile'))

        when:
        createWindowsSymbolicLink(new File(testDirectory, 'testDir'), testDirectory.createDir('symDir'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testDir'))
    }

    @Requires(TestPrecondition.WINDOWS)
    def 'does not detect Windows hard links as symbolic links'() {
        def symlink = new WindowsJdk7Symlink()
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        createWindowsHardLinks(new File(testDirectory, 'testFile'), testDirectory.createFile('symFile'))

        then:
        !symlink.isSymlink(new File(testDirectory, 'testFile'))
    }

    @Requires(TestPrecondition.WINDOWS)
    def 'can detect Windows junction point as symbolic links'() {
        def symlink = new WindowsJdk7Symlink()
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        createWindowsJunction(new File(testDirectory, 'testDir'), testDirectory.createDir('symDir'))
        println("IS DIRECTORY " + new File(testDirectory, 'testDir').isDirectory())

        then:
        symlink.isSymlink(new File(testDirectory, 'testDir'))
    }

    private static List<File> listSymlinkTestFiles() {
        def tempDir = new File(System.getProperty("java.io.tmpdir"))
        return tempDir.listFiles(new FileFilter() {
            @Override
            boolean accept(File pathname) {
                return pathname.name.startsWith("symlink") && (pathname.name.endsWith("test") || pathname.name.endsWith("test_link"))
            }
        })
    }

    private static void createWindowsSymbolicLink(File link, File target) {
        assert ["cmd", "/C", "mklink", "/D", link, target].execute().waitFor() == 0
    }

    private static void createWindowsJunction(File link, File target) {
        assert target.isDirectory(), "Windows junction only works on directory"
        assert ["cmd", "/C", "mklink", "/J", link, target].execute().waitFor() == 0
    }

    private static void createWindowsHardLinks(File link, File target) {
        assert target.isFile(), "Windows hard links only works on files"
        assertAdministrator()
        assert ["cmd", "/C", "mklink", "/H", link, target].execute().waitFor() == 0
    }

    // See: https://support.microsoft.com/en-us/help/243330/well-known-security-identifiers-in-windows-operating-systems
    private static final String WELL_KNOWN_ADMINISTRATORS_GROUP_SID = "S-1-5-32-544"
    private static void assertAdministrator() {
        assert new NTSystem().getGroupIDs().any { it == WELL_KNOWN_ADMINISTRATORS_GROUP_SID }
    }
}
