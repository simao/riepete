from fabric.api import local, run, cd, put, env, path
from fabric.contrib.project import rsync_project

env.use_ssh_config = True

def sync_bins():
    rsync_project("/opt/riepete", "./target/riepete-dist/", delete=True,
                  exclude=[".git", "log", "config/application.conf", "config/riepete.conf"],
                  extra_opts="--exclude-from .gitignore")

def compile_project():
    local("sbt dist")

def restart():
    run('sudo supervisorctl restart riepete')
    
def reset_permissions():
    run('chown riemann:riemann -R /opt/riepete')

def deploy():
    target_dir = '/opt/riepete'

    with cd(target_dir):
        compile_project()
        sync_bins()
        reset_permissions

    restart()
