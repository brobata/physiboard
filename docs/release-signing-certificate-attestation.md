---
header-includes:
  - \usepackage[margin=1.5cm]{geometry}
  - \usepackage{qrcode}
  - \usepackage{fancyvrb}
  - \setlength{\emergencystretch}{3em}
---

# Pastiera Release Signing Certificate Attestation

This document records the public certificate material for the official Pastiera release signing key.
It is intended to be used as a verification reference for official Pastiera release APK signatures.

\begin{center}
\qrcode[height=1.8in]{-----BEGIN CERTIFICATE-----MIIDfTCCAmWgAwIBAgIIanDIlbQMo34wDQYJKoZIhvcNAQEMBQAwbDELMAkGA1UEBhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEUMBIGA1UEChMLUGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQDEwhQYXN0aWVyYTAgFw0yNTExMTQxNzUyMjVaGA8yMDUzMDQwMTE3NTIyNVowbDELMAkGA1UEBhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEUMBIGA1UEChMLUGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQDEwhQYXN0aWVyYTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL1apXSJoi7VUp1nwUiDwvCKGJOWxUzfyZ08CHN09QmrmFzdO6Ln7xFhWBcRWIp/jssBirM9d4EVhcSgC/Tpw7WJvuqVe0TFNWnrDadEy/N7xKmW84xLL/cWHsiAGHLUEozT0Hj2hdmmG4+IDrbPXj5AFfLOHAC/9874u41w7YSGdUEjQSCTjdsdUc4aKOrgPSgKFTnLZyJ+Nz4vJdNY8gFaSSlDYqmotqkHb69eBHvnpReRE1o9fr8bfdBms7tIvv08shlkfXgZq5NZvwPKax2L9NNDrMbSDi/4cq0tK44EIau3PsSkGmAMObeFNuE19wBup4YWu+nDHZ8qJxcb/tcCAwEAAaMhMB8wHQYDVR0OBBYEFO0it4NBVLiWnE4qpJscsppEP8dWMA0GCSqGSIb3DQEBDAUAA4IBAQASjVR8xSz90V1DmjGdqvJCMe3k9cG0TIooLKRd0tjvUO4w8tfxIbAIhXyoZQ90zLiQzuItNWos+flbqQ2+yMaaJzBnCZOgps0/vkmM516ENWgqZqrx1Iv80NU9OooHbpWh3bKsNjKj0g7o/NYvnR4gM2C/MEb6WiPNrEIxYTbo28dhap3ScYh8aQI9Ae8mnMd3H6KN1SgMQA9OWbHRCl30D60dUY8onEKSr9I/fXLI1Xlir1AQcwHByw/0BB2jhflpuc9CUu53ug6OEEVpMwRZOIdTysjvmha5BCjuZy/bVGzqSQ8qwkON6aCH6Ert3Z0h2fz8zpByZDJICHVsbSmR-----END CERTIFICATE-----}
\end{center}

## Certificate Identity

\begin{Verbatim}[fontsize=\small]
Subject              CN=Pastiera, OU=Development, O=PalSoftware, L=Italy, ST=Italy, C=IT
Issuer               CN=Pastiera, OU=Development, O=PalSoftware, L=Italy, ST=Italy, C=IT
Validity start       2025-11-14 17:52:25 UTC
Validity end         2053-04-01 17:52:25 UTC
Signature algorithm  SHA256withRSA
Public key           RSA 2048-bit
\end{Verbatim}

## Certificate Fingerprints

\begin{Verbatim}[fontsize=\small]
SHA-256
D5:C0:18:B9:C3:3E:0C:DA:7C:EF:0B:00:6E:D7:39:D4
C6:30:4C:4E:F4:A0:C4:D9:45:4A:53:02:E7:C4:C3:E7

SHA-1
E4:33:54:0D:91:18:6D:70:90:13:6B:AF:35:4A:E5:60
19:5E:DA:29
\end{Verbatim}

\clearpage
\subsection*{Public Key (PEM)}

\begin{minipage}[t]{0.68\textwidth}
\begin{Verbatim}[fontsize=\tiny]
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvVqldImiLtVSnWfBSIPC
8IoYk5bFTN/JnTwIc3T1CauYXN07oufvEWFYFxFYin+OywGKsz13gRWFxKAL9OnD
tYm+6pV7RMU1aesNp0TL83vEqZbzjEsv9xYeyIAYctQSjNPQePaF2aYbj4gOts9e
PkAV8s4cAL/3zvi7jXDthIZ1QSNBIJON2x1Rzhoo6uA9KAoVOctnIn43Pi8l01jy
AVpJKUNiqai2qQdvr14Ee+elF5ETWj1+vxt90Gazu0i+/TyyGWR9eBmrk1m/A8pr
HYv000OsxtIOL/hyrS0rjgQhq7c+xKQaYAw5t4U24TX3AG6nhha76cMdnyonFxv+
1wIDAQAB
-----END PUBLIC KEY-----
\end{Verbatim}
\end{minipage}\hfill
\begin{minipage}[t]{0.28\textwidth}
\vspace{0pt}
\raggedleft
\qrcode[height=1.25in]{-----BEGIN PUBLIC KEY-----MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvVqldImiLtVSnWfBSIPC8IoYk5bFTN/JnTwIc3T1CauYXN07oufvEWFYFxFYin+OywGKsz13gRWFxKAL9OnDtYm+6pV7RMU1aesNp0TL83vEqZbzjEsv9xYeyIAYctQSjNPQePaF2aYbj4gOts9ePkAV8s4cAL/3zvi7jXDthIZ1QSNBIJON2x1Rzhoo6uA9KAoVOctnIn43Pi8l01jyAVpJKUNiqai2qQdvr14Ee+elF5ETWj1+vxt90Gazu0i+/TyyGWR9eBmrk1m/A8prHYv000OsxtIOL/hyrS0rjgQhq7c+xKQaYAw5t4U24TX3AG6nhha76cMdnyonFxv+1wIDAQAB-----END PUBLIC KEY-----}
\end{minipage}

\subsection*{Certificate (PEM)}

\begin{minipage}[t]{0.68\textwidth}
\begin{Verbatim}[fontsize=\tiny]
-----BEGIN CERTIFICATE-----
MIIDfTCCAmWgAwIBAgIIanDIlbQMo34wDQYJKoZIhvcNAQEMBQAwbDELMAkGA1UE
BhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEUMBIGA1UEChML
UGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQDEwhQYXN0
aWVyYTAgFw0yNTExMTQxNzUyMjVaGA8yMDUzMDQwMTE3NTIyNVowbDELMAkGA1UE
BhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEUMBIGA1UEChML
UGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQDEwhQYXN0
aWVyYTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL1apXSJoi7VUp1n
wUiDwvCKGJOWxUzfyZ08CHN09QmrmFzdO6Ln7xFhWBcRWIp/jssBirM9d4EVhcSg
C/Tpw7WJvuqVe0TFNWnrDadEy/N7xKmW84xLL/cWHsiAGHLUEozT0Hj2hdmmG4+I
DrbPXj5AFfLOHAC/9874u41w7YSGdUEjQSCTjdsdUc4aKOrgPSgKFTnLZyJ+Nz4v
JdNY8gFaSSlDYqmotqkHb69eBHvnpReRE1o9fr8bfdBms7tIvv08shlkfXgZq5NZ
vwPKax2L9NNDrMbSDi/4cq0tK44EIau3PsSkGmAMObeFNuE19wBup4YWu+nDHZ8q
Jxcb/tcCAwEAAaMhMB8wHQYDVR0OBBYEFO0it4NBVLiWnE4qpJscsppEP8dWMA0G
CSqGSIb3DQEBDAUAA4IBAQASjVR8xSz90V1DmjGdqvJCMe3k9cG0TIooLKRd0tjv
UO4w8tfxIbAIhXyoZQ90zLiQzuItNWos+flbqQ2+yMaaJzBnCZOgps0/vkmM516E
NWgqZqrx1Iv80NU9OooHbpWh3bKsNjKj0g7o/NYvnR4gM2C/MEb6WiPNrEIxYTbo
28dhap3ScYh8aQI9Ae8mnMd3H6KN1SgMQA9OWbHRCl30D60dUY8onEKSr9I/fXLI
1Xlir1AQcwHByw/0BB2jhflpuc9CUu53ug6OEEVpMwRZOIdTysjvmha5BCjuZy/b
VGzqSQ8qwkON6aCH6Ert3Z0h2fz8zpByZDJICHVsbSmR
-----END CERTIFICATE-----
\end{Verbatim}
\end{minipage}\hfill
\begin{minipage}[t]{0.28\textwidth}
\vspace{0pt}
\raggedleft
\qrcode[height=1.25in]{-----BEGIN CERTIFICATE-----MIIDfTCCAmWgAwIBAgIIanDIlbQMo34wDQYJKoZIhvcNAQEMBQAwbDELMAkGA1UEBhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEUMBIGA1UEChMLUGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQDEwhQYXN0aWVyYTAgFw0yNTExMTQxNzUyMjVaGA8yMDUzMDQwMTE3NTIyNVowbDELMAkGA1UEBhMCSVQxDjAMBgNVBAgTBUl0YWx5MQ4wDAYDVQQHEwVJdGFseTEUMBIGA1UEChMLUGFsU29mdHdhcmUxFDASBgNVBAsTC0RldmVsb3BtZW50MREwDwYDVQQDEwhQYXN0aWVyYTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL1apXSJoi7VUp1nwUiDwvCKGJOWxUzfyZ08CHN09QmrmFzdO6Ln7xFhWBcRWIp/jssBirM9d4EVhcSgC/Tpw7WJvuqVe0TFNWnrDadEy/N7xKmW84xLL/cWHsiAGHLUEozT0Hj2hdmmG4+IDrbPXj5AFfLOHAC/9874u41w7YSGdUEjQSCTjdsdUc4aKOrgPSgKFTnLZyJ+Nz4vJdNY8gFaSSlDYqmotqkHb69eBHvnpReRE1o9fr8bfdBms7tIvv08shlkfXgZq5NZvwPKax2L9NNDrMbSDi/4cq0tK44EIau3PsSkGmAMObeFNuE19wBup4YWu+nDHZ8qJxcb/tcCAwEAAaMhMB8wHQYDVR0OBBYEFO0it4NBVLiWnE4qpJscsppEP8dWMA0GCSqGSIb3DQEBDAUAA4IBAQASjVR8xSz90V1DmjGdqvJCMe3k9cG0TIooLKRd0tjvUO4w8tfxIbAIhXyoZQ90zLiQzuItNWos+flbqQ2+yMaaJzBnCZOgps0/vkmM516ENWgqZqrx1Iv80NU9OooHbpWh3bKsNjKj0g7o/NYvnR4gM2C/MEb6WiPNrEIxYTbo28dhap3ScYh8aQI9Ae8mnMd3H6KN1SgMQA9OWbHRCl30D60dUY8onEKSr9I/fXLI1Xlir1AQcwHByw/0BB2jhflpuc9CUu53ug6OEEVpMwRZOIdTysjvmha5BCjuZy/bVGzqSQ8qwkON6aCH6Ert3Z0h2fz8zpByZDJICHVsbSmR-----END CERTIFICATE-----}
\end{minipage}

## Verification Note

Any official Pastiera release APK signed with the official release key should present the certificate fingerprint listed above.

## Linux Verification

Use standard Android SDK command-line tools:

```bash
APK=/path/to/Pastiera084.apk
EXPECTED="$(cat <<'EOF'
d5c018b9c33e0cda7cef0b006ed739d4
c6304c4ef4a0c4d9454a5302e7c4c3e7
EOF
)"
EXPECTED="$(printf '%s' "$EXPECTED" | tr -d '\n' | tr '[:upper:]' '[:lower:]')"

ACTUAL="$(
  apksigner verify --print-certs "$APK" |
  awk -F': ' '/certificate SHA-256 digest/ { print tolower($2); exit }'
)"

printf 'Expected certificate SHA-256: %s\n' "$EXPECTED"
printf 'Actual certificate SHA-256:   %s\n' "$ACTUAL"

if [ "$ACTUAL" = "$EXPECTED" ]; then
  echo 'OK: APK is signed with the official Pastiera release certificate.'
else
  echo 'NOT OK: APK does not match the official Pastiera release certificate.'
  exit 1
fi
```

If `OK` is printed, the APK matches the official Pastiera release signing certificate recorded in this document.
