using System.Text.Json;
using Windows.Management.Deployment;

var packages = new List<InstalledPackage>();
var packageManager = new PackageManager();

foreach (var package in packageManager.FindPackagesForUser(""))
{
    try
    {
        if (package.IsFramework || package.InstalledLocation is null)
        {
            continue;
        }

        packages.Add(new InstalledPackage(
            package.Id.Name,
            package.Id.FamilyName,
            package.Id.FullName,
            package.InstalledLocation.Path));
    }
    catch
    {
        // Some packages can fail property access because of permissions or transient registration state.
    }
}

Console.WriteLine(JsonSerializer.Serialize(packages));

internal sealed record InstalledPackage(
    string Name,
    string PackageFamilyName,
    string PackageFullName,
    string InstallLocation);
