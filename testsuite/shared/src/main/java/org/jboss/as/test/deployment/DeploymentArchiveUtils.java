package org.jboss.as.test.deployment;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.exporter.zip.ZipExporterImpl;
import org.jboss.shrinkwrap.impl.base.path.BasicPath;

import java.io.File;

/**
 * @author Vratislav Marek (vmarek@redhat.com)
 * @since 30.1.18 13:00
 **/
public class DeploymentArchiveUtils {

    private DeploymentArchiveUtils() {
        //
    }

    public static File createWarArchive() {
        return createWarArchive("default-cli-test-app-deplo.war", "Version0");
    }

    public static File createWarArchive(String archiveName, String content) {

        WebArchive war = ShrinkWrap.create(WebArchive.class, archiveName);
        war.addAsWebResource(new StringAsset(content), "page.html");

        final String tempDir = TestSuiteEnvironment.getTmpDir();
        File file = new File(tempDir, war.getName());
        new ZipExporterImpl(war).exportTo(file, true);
        return file;
    }

    public static File createCliArchive() {
        return createCliArchive("deploymentarchive.cli", "ls -l");
    }

    public static File createCliArchive(String archiveName, String content) {

        final GenericArchive cliArchive = ShrinkWrap.create(GenericArchive.class, archiveName);
        cliArchive.add(new StringAsset(content), "deploy.scr");

        final String tempDir = TestSuiteEnvironment.getTmpDir();
        final File file = new File(tempDir, cliArchive.getName());
        cliArchive.as(ZipExporter.class).exportTo(file, true);
        return file;
    }

    public static File createEnterpriseArchive() {
        return createEnterpriseArchive("cli-test-app-deploy-all.ear",
                "cli-test-app3-deploy-all.war", "Version3");
    }

    public static File createEnterpriseArchive(String archiveName, String subArchiveName, String content) {

        WebArchive war = ShrinkWrap.create(WebArchive.class, archiveName);
        war.addAsWebResource(new StringAsset(content), "page.html");
        final EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class,
                subArchiveName);
        ear.add(war, new BasicPath("/"), ZipExporter.class);

        final String tempDir = TestSuiteEnvironment.getTmpDir();
        File file = new File(tempDir, ear.getName());
        new ZipExporterImpl(ear).exportTo(file, true);
        return file;
    }
}
