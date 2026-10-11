#!/usr/bin/env python3
"""Focused actual gate authorization regression. Run only after package completion.

Usage: python3 validation/check_control_gate_operation.py <compiled-bootstrap-directory>
Reuses the coordinated full-bootstrap compilation; only compiles this fixture.
Requires JDK 17+ with java.instrument and JDK-internal ASM. No Forge or native DLL.
"""
from pathlib import Path
import subprocess
import os
import sys
import tempfile
import zipfile

if len(sys.argv) != 2:
    raise SystemExit(__doc__)
root = Path(__file__).resolve().parents[1]
bootstrap = Path(sys.argv[1]).resolve()
if not (bootstrap / 'dev/ronova/pro/bootstrap/TaskBridge.class').is_file():
    raise SystemExit('Supply the coordinated full-bootstrap compilation directory')
exports = ['--add-exports', 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED']
with tempfile.TemporaryDirectory(prefix='ronova-control-gate-') as temporary:
    output = Path(temporary)
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-proc:none', '-encoding', 'UTF-8', *exports,
                    '-cp', str(bootstrap), '-d', str(output), str(root / 'validation/control-gate/ControlGateOperationCheck.java'),
                    str(root / 'validation/control-gate/ControlGateTarget.java')], check=True)
    target = output / 'gate-target.jar'
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.write(output / 'check/ControlGateTarget.class', 'check/ControlGateTarget.class')
    agent = output / 'gate-check.jar'
    with zipfile.ZipFile(agent, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nPremain-Class: check.ControlGateOperationCheck\nCan-Retransform-Classes: true\n\n')
        for compiled in output.rglob('*.class'):
            archive.write(compiled, compiled.relative_to(output).as_posix())
    subprocess.run(['java', *exports, '--add-opens', 'java.base/java.util.concurrent.locks=ALL-UNNAMED',
                    '-Xbootclasspath/a:' + os.pathsep.join([str(bootstrap), str(target)]), '-javaagent:' + str(agent), '-cp', str(output),
                    'check.ControlGateOperationCheck'], check=True, timeout=30)
