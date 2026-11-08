; Built by `gradlew packageInstaller`, which passes /DAppVersion and /DSourceDir (the jpackage app-image).
[Setup]
AppId={{B3C1D5A2-7E4F-4B8A-9C6D-2F1E8A5D7C30}
AppName=SoundpadPlus
AppVersion={#AppVersion}
AppPublisher=system32developer
AppPublisherURL=https://github.com/system32developer/SoundpadPlus
; Per-user install next to its settings, plugins and tools, so no admin rights are needed (same folder as the old MSI).
PrivilegesRequired=lowest
DefaultDirName={localappdata}\SoundpadPlus
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes
RestartApplications=no
UninstallDisplayIcon={app}\SoundpadPlus.exe
OutputDir={#OutputDir}
OutputBaseFilename=SoundpadPlus-{#AppVersion}
Compression=lzma2/ultra
SolidCompression=yes
WizardStyle=modern
; The language list is shown with the one that matches Windows already selected.
ShowLanguageDialog=yes
LanguageDetectionMethod=uilanguage

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs ignoreversion
Source: "{#ExamplesDir}\tuna.json"; DestDir: "{app}\plugins"; Flags: onlyifdoesntexist

[Icons]
Name: "{autoprograms}\SoundpadPlus"; Filename: "{app}\SoundpadPlus.exe"
Name: "{autodesktop}\SoundpadPlus"; Filename: "{app}\SoundpadPlus.exe"; Tasks: desktopicon

[Run]
; The "open the program" checkbox on the last page.
Filename: "{app}\SoundpadPlus.exe"; Description: "{cm:LaunchProgram,SoundpadPlus}"; Flags: nowait postinstall skipifsilent

[Code]
{ Versions up to 1.0.8 came as an MSI. Remove it (user data stays) so there is only one entry in Installed apps. }
function FindOldMsi: String;
var
  Keys: TArrayOfString;
  I: Integer;
  Name, Uninstall: String;
begin
  Result := '';
  if RegGetSubkeyNames(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall', Keys) then
    for I := 0 to GetArrayLength(Keys) - 1 do
      if (Copy(Keys[I], 1, 1) = '{') and
         RegQueryStringValue(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\' + Keys[I], 'DisplayName', Name) and
         (Pos('SoundpadPlus', Name) = 1) and
         RegQueryStringValue(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\' + Keys[I], 'UninstallString', Uninstall) and
         (Pos('msiexec', LowerCase(Uninstall)) > 0) then
      begin
        Result := Keys[I];
        Exit;
      end;
  if RegGetSubkeyNames(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall', Keys) then
    for I := 0 to GetArrayLength(Keys) - 1 do
      if (Copy(Keys[I], 1, 1) = '{') and
         RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\' + Keys[I], 'DisplayName', Name) and
         (Pos('SoundpadPlus', Name) = 1) then
      begin
        Result := Keys[I];
        Exit;
      end;
end;

{ The MSI wipes the install folder when it is removed, so the user's data is moved out of the way first and back afterwards. }
procedure MoveUserData(const FromDir, ToDir: String);
var
  Names: array[0..3] of String;
  I, ResultCode: Integer;
begin
  Names[0] := 'settings.properties';
  Names[1] := 'plugins';
  Names[2] := 'lang';
  Names[3] := 'tools';
  for I := 0 to 3 do
    if FileExists(FromDir + '\' + Names[I]) or DirExists(FromDir + '\' + Names[I]) then
      Exec(ExpandConstant('{cmd}'), '/c move "' + FromDir + '\' + Names[I] + '" "' + ToDir + '\' + Names[I] + '"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Code, AppDir, Backup, Args: String;
  ResultCode: Integer;
begin
  if CurStep = ssInstall then
  begin
    Code := FindOldMsi;
    if Code = '' then Exit;
    AppDir := ExpandConstant('{app}');
    Backup := AppDir + '.userdata';
    ForceDirectories(Backup);
    MoveUserData(AppDir, Backup);
    Args := '/x ' + Code + ' /qn /norestart';
    { A machine-wide MSI needs administrator rights: ask for them if the quiet attempt fails. }
    if not Exec(ExpandConstant('{sys}\msiexec.exe'), Args, '', SW_HIDE, ewWaitUntilTerminated, ResultCode) or (ResultCode <> 0) then
      ShellExec('runas', ExpandConstant('{sys}\msiexec.exe'), Args, '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    ForceDirectories(AppDir);
    MoveUserData(Backup, AppDir);
    RemoveDir(Backup);
  end;
end;
