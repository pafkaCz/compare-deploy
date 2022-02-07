import codecs
import os
import subprocess

def runGitCommand(cmd, directory):
    print(cmd)
    pr = subprocess.Popen(cmd, cwd = os.path.dirname(directory), shell = True, stdout = subprocess.PIPE, stderr = subprocess.PIPE )
    (out, error) = pr.communicate(timeout=15)


    print("Err : " + str(error))
    print("Out : " + str(out))
    print("Exit code : " + str(pr.returncode))
    assert pr.returncode == 0


print("Starting updating SC services.")

k8sBeServices = ["be-organization", "be-process", "be-task", "be-user"]
k8sBlServices = ["bl-case-event"]
k8sFeapiServices = ["feapi-case-simple", "feapi-case-storage"]

basePath = "C:/Users/e_pzeman/Projects/BSSC"
gitPatchName = "git.patch"
filename = basePath + "/" + gitPatchName
contents = codecs.open(filename, encoding='utf-8').read()

k8sServices = [
    "be-artemis-dispatcher",
    "be-organization",
    "be-permission",
    "be-process",
    "be-task",
    "be-user",

    "bl-case-event",
    "bl-case-storage",
    "bl-case-storage-customer",
    "bl-case-storage-product",
    "bl-case-storage-user",
    "bl-case-template",
    "bl-document-id-convertor",
    "bl-notification-dashboard",
    "bl-redirect",
    "bl-reporting-storage",
    "bl-simple-case",
    "bl-text-formatting",
    "bl-user-setting",
    "bl-utq-saved-search",

    "feapi-case-simple",
    "feapi-case-storage",
    "feapi-case-template",
    "feapi-customer",
    "feapi-history-and-comments",
    "feapi-identity",
    "feapi-notification",
    "feapi-organization",
    "feapi-product",
    "feapi-redirect",
    "feapi-task",
    "feapi-ucf",
    "feapi-user-setting",
    "feapi-utq-saved-search",

    "int-bpm-event-aggregator",
    "int-case",
    "int-case-event",
    "int-casebusiness-audit",
    "int-caseevent-mng",
    "int-casetocustomer",
    "int-casetocustomer-v2",
    "int-casetoproduct",
    "int-casetouser",
    "int-document-caseevent",
    "int-sc-permission",
    "int-simple-case",
    "int-task-queue",
    "int-user",
    "int-user-setting",

    "new-simple-case"

    "pf-config"
    "pf-messaging-config"
    "pf-permissions"
]
servicesInError = []
oldBranchName = "feature/BSCM-5578-merge_dev_to_master"
newBranchName = "feature/BSCM-5578-merge_dev_to_master"
for project in k8sServices:
    print("Processing %s" % project)
    projectDirectory = basePath + "/" + project + "/"
    projectPatchFilename = projectDirectory + gitPatchName
    try:

        # // MERGE DEVELOP to master
        """""
        runGitCommand("git checkout develop", projectDirectory)
        runGitCommand("git pull", projectDirectory)
        runGitCommand("git branch -d " + oldBranchName, projectDirectory)
        runGitCommand("git checkout -B master origin/master", projectDirectory)
        runGitCommand("git pull", projectDirectory)
        runGitCommand("git checkout -B " + newBranchName, projectDirectory)
        runGitCommand("git merge develop", projectDirectory)
        runGitCommand("git push origin " + newBranchName, projectDirectory)
        """

        # // DELETE local branch + update
        runGitCommand("git checkout develop", projectDirectory)
        runGitCommand("git pull", projectDirectory)
        runGitCommand("git branch -d " + oldBranchName, projectDirectory)
        runGitCommand("git checkout -B master origin/master", projectDirectory)
        runGitCommand("git pull", projectDirectory)


        # APPLY PATCH
        # runGitCommand("git checkout master", projectDirectory)
        # runGitCommand("git pull", projectDirectory)
        # runGitCommand("git branch -d " + newBranchName, projectDirectory)
        # runGitCommand("git branch -d develop", projectDirectory)

        # projectPatchFile = open(projectPatchFilename, "w")
        # projectPatchFile.write(contents.replace("${projectName}", project))
        # projectPatchFile.close()

        # runGitCommand("git apply " + projectPatchFilename, projectDirectory)
        # runGitCommand("git add ./deployment", projectDirectory)
        # runGitCommand("git commit -m \"PSLAS-998 - SC logging in new logstash\"", projectDirectory)
        # runGitCommand("git push origin feature/PSLAS-998_logging_SC_logstash", projectDirectory)


    except:
        servicesInError.append(project)
    finally:
        print("END")
        # os.remove(projectPatchFilename)
        # runGitCommand("git checkout develop", projectDirectory)
        # runGitCommand("git branch -d feature/PSLAS-998_logging_SC_logstash", projectDirectory)


if not servicesInError:
    print("SUCCESS!")
else:
    print("ERROR in  ")
    print(*servicesInError, sep = "\", \"")  
    

        