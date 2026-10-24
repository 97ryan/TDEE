#!/usr/bin/env python3
"""Prints an Android lint XML report as one line per finding."""
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
issues = root.findall("issue")
order = {"Fatal": 0, "Error": 1, "Warning": 2, "Informational": 3}
issues.sort(key=lambda i: order.get(i.get("severity"), 9))
for issue in issues:
    loc = issue.find("location")
    where = f'{loc.get("file", "?").split("/app/")[-1]}:{loc.get("line", "?")}' if loc is not None else "?"
    print(f'{issue.get("severity"):8} {issue.get("id"):28} {where}  {issue.get("message")}')
print(f"\n{len(issues)} lint findings")
