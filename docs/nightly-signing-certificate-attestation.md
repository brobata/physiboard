---
header-includes:
  - \usepackage[margin=1.5cm]{geometry}
  - \usepackage{qrcode}
  - \usepackage{fancyvrb}
  - \setlength{\emergencystretch}{3em}
---

# Pastiera Nightly Signing Certificate Attestation

This document records the public certificate material for the shared Pastiera Nightly signing key.
It is intended to be used as a verification reference for Pastiera Nightly APK signatures.

\begin{center}
\qrcode[height=1.8in]{-----BEGIN CERTIFICATE-----MIIDjjCCAnagAwIBAgIJAJCddPjVONvfMA0GCSqGSIb3DQEBCwUAMHQxCzAJBgNVBAYTAklUMQ4wDAYDVQQIEwVJdGFseTEOMAwGA1UEBxMFSXRhbHkxFDASBgNVBAoTC1BhbFNvZnR3YXJlMRQwEgYDVQQLEwtEZXZlbG9wbWVudDEZMBcGA1UEAxMQUGFzdGllcmEgTmlnaHRseTAgFw0yNjAzMDYyMDM0MzhaGA8yMDUzMDcyMjIwMzQzOFowdDELMAkGA1UEBhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEUMBIGA1UEChMLUGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MRkwFwYDVQQDExBQYXN0aWVyYSBOaWdodGx5MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv3DSu5JTNXvBhfJOegbvT8UoR59mbRCPQe3bD8liFfSN6XWRN06/MQ/yV31TN2rYVv3oLiW3fPk36n27HRgXlyTF4rEiNDIEU3qRDZzgOSkzfsxhIXq4+r8tjR1sKFDvO9IJLV4y1FZ8pVMpoaPIJj5Tq1Ivo7dH/brcYXF23tt+5R1BY3XvhWlUyV+spsd1Wh5rIMFd69K8IVoGggZJxZTcNcdfG/CGewBMZHYNLkDnWSAMQKBagrmhXQOAkkXMwlyEH6PKXdqK1E6CFB/yJN3Kl+DM1ERF8mcKZ2cTUyVtJGKmKZbl8pAAob+mAkHY5j98YVrEZvoyMsJX+yNXVwIDAQABoyEwHzAdBgNVHQ4EFgQUf4isNt6UVrvHCOqbghzyI6DUh8YwDQYJKoZIhvcNAQELBQADggEBAHRfMLwPuyFHylA5Z9mLA8GOnVADXcKO9qaFVDtfP5b22YqvplD4JwL+S/Cl7i0LQBzhQz5fYul+xnxwN99D7CgOAzs+XuRUQBx4ygM6+31wuNPWSGWWeqBiouBhgoARyGKSHjq5UVL7qcXvqqjA/ONtBYhiwuXm6FJFqFrGao/GkDROwvLI53OVmugBCR4tLy9/ISnTNMvyahfhkRRP4hA/ybmAqR0LJwsfCZtvyNn50/7jKBi/IsHQbXSD3aTAEOuVmb5Jv+p0YNeK5fansZgg5hejJRqz1JXz6y6ZEgivFiGwdLxZdpwge8nWc/hZ9DUT6jepmELZPHB3+8Ezn70=-----END CERTIFICATE-----}
\end{center}

## Certificate Identity

\begin{Verbatim}[fontsize=\small]
Subject              CN=Pastiera Nightly, OU=Development, O=PalSoftware, L=Italy, ST=Italy, C=IT
Issuer               CN=Pastiera Nightly, OU=Development, O=PalSoftware, L=Italy, ST=Italy, C=IT
Alias                pastiera-nightly
Validity start       Fri Mar 06 21:34:38 CET 2026
Validity end         Tue Jul 22 22:34:38 CEST 2053
Serial number        909d74f8d538dbdf
Signature algorithm  SHA256withRSA
Public key           RSA 2048-bit
\end{Verbatim}

## Certificate Fingerprints

\begin{Verbatim}[fontsize=\small]
SHA-256
8C:5D:CE:86:0A:65:A7:A3:C3:BE:FC:B7:F7:F3:5A:1F
35:23:C1:D0:14:62:27:1D:6A:E0:3F:4D:F4:02:E6:85

SHA-1
44:A0:1A:2E:E4:9D:EF:53:9B:3C
CC:63:BF:77:E6:7B:85:74:A1:5B
\end{Verbatim}

\clearpage
\subsection*{Public Key (PEM)}

\begin{minipage}[t]{0.68\textwidth}
\begin{Verbatim}[fontsize=\tiny]
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv3DSu5JTNXvBhfJOegbv
T8UoR59mbRCPQe3bD8liFfSN6XWRN06/MQ/yV31TN2rYVv3oLiW3fPk36n27HRgX
lyTF4rEiNDIEU3qRDZzgOSkzfsxhIXq4+r8tjR1sKFDvO9IJLV4y1FZ8pVMpoaPI
Jj5Tq1Ivo7dH/brcYXF23tt+5R1BY3XvhWlUyV+spsd1Wh5rIMFd69K8IVoGggZJ
xZTcNcdfG/CGewBMZHYNLkDnWSAMQKBagrmhXQOAkkXMwlyEH6PKXdqK1E6CFB/y
JN3Kl+DM1ERF8mcKZ2cTUyVtJGKmKZbl8pAAob+mAkHY5j98YVrEZvoyMsJX+yNX
VwIDAQAB
-----END PUBLIC KEY-----
\end{Verbatim}
\end{minipage}\hfill
\begin{minipage}[t]{0.28\textwidth}
\vspace{0pt}
\raggedleft
\qrcode[height=1.25in]{-----BEGIN PUBLIC KEY-----MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv3DSu5JTNXvBhfJOegbvT8UoR59mbRCPQe3bD8liFfSN6XWRN06/MQ/yV31TN2rYVv3oLiW3fPk36n27HRgXlyTF4rEiNDIEU3qRDZzgOSkzfsxhIXq4+r8tjR1sKFDvO9IJLV4y1FZ8pVMpoaPIJj5Tq1Ivo7dH/brcYXF23tt+5R1BY3XvhWlUyV+spsd1Wh5rIMFd69K8IVoGggZJxZTcNcdfG/CGewBMZHYNLkDnWSAMQKBagrmhXQOAkkXMwlyEH6PKXdqK1E6CFB/yJN3Kl+DM1ERF8mcKZ2cTUyVtJGKmKZbl8pAAob+mAkHY5j98YVrEZvoyMsJX+yNXVwIDAQAB-----END PUBLIC KEY-----}
\end{minipage}

\subsection*{Certificate (PEM)}

\begin{minipage}[t]{0.68\textwidth}
\begin{Verbatim}[fontsize=\tiny]
-----BEGIN CERTIFICATE-----
MIIDjjCCAnagAwIBAgIJAJCddPjVONvfMA0GCSqGSIb3DQEBCwUAMHQxCzAJBgNV
BAYTAklUMQ4wDAYDVQQIEwVJdGFseTEOMAwGA1UEBxMFSXRhbHkxFDASBgNVBAoT
C1BhbFNvZnR3YXJlMRQwEgYDVQQLEwtEZXZlbG9wbWVudDEZMBcGA1UEAxMQUGFz
dGllcmEgTmlnaHRseTAgFw0yNjAzMDYyMDM0MzhaGA8yMDUzMDcyMjIwMzQzOFow
dDELMAkGA1UEBhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEU
MBIGA1UEChMLUGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MRkwFwYD
VQQDExBQYXN0aWVyYSBOaWdodGx5MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB
CgKCAQEAv3DSu5JTNXvBhfJOegbvT8UoR59mbRCPQe3bD8liFfSN6XWRN06/MQ/y
V31TN2rYVv3oLiW3fPk36n27HRgXlyTF4rEiNDIEU3qRDZzgOSkzfsxhIXq4+r8t
jR1sKFDvO9IJLV4y1FZ8pVMpoaPIJj5Tq1Ivo7dH/brcYXF23tt+5R1BY3XvhWlU
yV+spsd1Wh5rIMFd69K8IVoGggZJxZTcNcdfG/CGewBMZHYNLkDnWSAMQKBagrmh
XQOAkkXMwlyEH6PKXdqK1E6CFB/yJN3Kl+DM1ERF8mcKZ2cTUyVtJGKmKZbl8pAA
ob+mAkHY5j98YVrEZvoyMsJX+yNXVwIDAQABoyEwHzAdBgNVHQ4EFgQUf4isNt6U
VrvHCOqbghzyI6DUh8YwDQYJKoZIhvcNAQELBQADggEBAHRfMLwPuyFHylA5Z9mL
A8GOnVADXcKO9qaFVDtfP5b22YqvplD4JwL+S/Cl7i0LQBzhQz5fYul+xnxwN99D
7CgOAzs+XuRUQBx4ygM6+31wuNPWSGWWeqBiouBhgoARyGKSHjq5UVL7qcXvqqjA
/ONtBYhiwuXm6FJFqFrGao/GkDROwvLI53OVmugBCR4tLy9/ISnTNMvyahfhkRRP
4hA/ybmAqR0LJwsfCZtvyNn50/7jKBi/IsHQbXSD3aTAEOuVmb5Jv+p0YNeK5fan
sZgg5hejJRqz1JXz6y6ZEgivFiGwdLxZdpwge8nWc/hZ9DUT6jepmELZPHB3+8Ez
n70=
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill
\begin{minipage}[t]{0.28\textwidth}
\vspace{0pt}
\raggedleft
\qrcode[height=1.25in]{-----BEGIN CERTIFICATE-----MIIDjjCCAnagAwIBAgIJAJCddPjVONvfMA0GCSqGSIb3DQEBCwUAMHQxCzAJBgNVBAYTAklUMQ4wDAYDVQQIEwVJdGFseTEOMAwGA1UEBxMFSXRhbHkxFDASBgNVBAoTC1BhbFNvZnR3YXJlMRQwEgYDVQQLEwtEZXZlbG9wbWVudDEZMBcGA1UEAxMQUGFzdGllcmEgTmlnaHRseTAgFw0yNjAzMDYyMDM0MzhaGA8yMDUzMDcyMjIwMzQzOFowdDELMAkGA1UEBhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEUMBIGA1UEChMLUGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MRkwFwYDVQQDExBQYXN0aWVyYSBOaWdodGx5MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv3DSu5JTNXvBhfJOegbvT8UoR59mbRCPQe3bD8liFfSN6XWRN06/MQ/yV31TN2rYVv3oLiW3fPk36n27HRgXlyTF4rEiNDIEU3qRDZzgOSkzfsxhIXq4+r8tjR1sKFDvO9IJLV4y1FZ8pVMpoaPIJj5Tq1Ivo7dH/brcYXF23tt+5R1BY3XvhWlUyV+spsd1Wh5rIMFd69K8IVoGggZJxZTcNcdfG/CGewBMZHYNLkDnWSAMQKBagrmhXQOAkkXMwlyEH6PKXdqK1E6CFB/yJN3Kl+DM1ERF8mcKZ2cTUyVtJGKmKZbl8pAAob+mAkHY5j98YVrEZvoyMsJX+yNXVwIDAQABoyEwHzAdBgNVHQ4EFgQUf4isNt6UVrvHCOqbghzyI6DUh8YwDQYJKoZIhvcNAQELBQADggEBAHRfMLwPuyFHylA5Z9mLA8GOnVADXcKO9qaFVDtfP5b22YqvplD4JwL+S/Cl7i0LQBzhQz5fYul+xnxwN99D7CgOAzs+XuRUQBx4ygM6+31wuNPWSGWWeqBiouBhgoARyGKSHjq5UVL7qcXvqqjA/ONtBYhiwuXm6FJFqFrGao/GkDROwvLI53OVmugBCR4tLy9/ISnTNMvyahfhkRRP4hA/ybmAqR0LJwsfCZtvyNn50/7jKBi/IsHQbXSD3aTAEOuVmb5Jv+p0YNeK5fansZgg5hejJRqz1JXz6y6ZEgivFiGwdLxZdpwge8nWc/hZ9DUT6jepmELZPHB3+8Ezn70=-----END CERTIFICATE-----}
\end{minipage}

## Verification Note

Any Pastiera Nightly APK signed with the shared nightly key should present the certificate fingerprint listed above.

## Linux Verification

Use standard Android SDK command-line tools:

```bash
APK=/path/to/app-nightly-release.apk
EXPECTED="8c5dce860a65a7a3c3befcb7f7f35a1f3523c1d01462271d6ae03f4df402e685"
ACTUAL="$(
  apksigner verify --print-certs "$APK" |
  awk -F': ' '/certificate SHA-256 digest/ { print tolower($2); exit }'
)"

printf 'Expected certificate SHA-256: %s\n' "$EXPECTED"
printf 'Actual certificate SHA-256:   %s\n' "$ACTUAL"

if [ "$ACTUAL" = "$EXPECTED" ]; then
  echo 'OK: APK is signed with the shared Pastiera Nightly certificate.'
else
  echo 'NOT OK: APK does not match the shared Pastiera Nightly certificate.'
  exit 1
fi
```

If `OK` is printed, the APK matches the shared Pastiera Nightly signing certificate recorded in this document.
