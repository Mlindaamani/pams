import { Suspense, lazy, useEffect, useMemo } from "react";
import type { ClassKey } from "keycloakify/login";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "./Template";
import "./main.css";

import DeleteAccountConfirm from "./pages/DeleteAccountConfirm.tsx";
import { createTheme, ThemeProvider } from "@mui/material";
import createCache from "@emotion/cache";
import { CacheProvider } from "@emotion/react";
import { prefixer } from "stylis";
import rtlPlugin from "stylis-plugin-rtl";
import { createPamsThemeOptions } from "../../../shared/pamsTheme";
const UserProfileFormFields = lazy(() => import("./UserProfileFormFields"));
const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const LoginUsername = lazy(() => import("./pages/LoginUsername"));
const LoginPassword = lazy(() => import("./pages/LoginPassword"));
const LoginResetPassword = lazy(() => import("./pages/LoginResetPassword"));
const LoginPasskeysConditionalAuthenticate = lazy(
    () => import("./pages/LoginPasskeysConditionalAuthenticate")
);
const Terms = lazy(() => import("./pages/Terms"));
const Error = lazy(() => import("./pages/Error"));
const Code = lazy(() => import("./pages/Code"));
const SelectAuthenticator = lazy(() => import("./pages/SelectAuthenticator"));

const LoginVerifyEmail = lazy(() => import("./pages/LoginVerifyEmail"));
const LoginUpdateProfile = lazy(() => import("./pages/LoginUpdateProfile"));
const LoginUpdatePassword = lazy(() => import("./pages/LoginUpdatePassword"));
const LoginResetOtp = lazy(() => import("./pages/LoginResetOtp"));
const LoginPageExpired = lazy(() => import("./pages/LoginPageExpired"));
const LoginOtp = lazy(() => import("./pages/LoginOtp"));
const LoginIdpLinkEmail = lazy(() => import("./pages/LoginIdpLinkEmail"));
const LoginIdpLinkConfirm = lazy(() => import("./pages/LoginIdpLinkConfirm"));
const SamlPostForm = lazy(() => import("./pages/SamlPostForm"));
const LogoutConfirm = lazy(() => import("./pages/LogoutConfirm"));
const LoginX509Info = lazy(() => import("./pages/LoginX509Info"));
const Info = lazy(() => import("./pages/Info"));
const UpdateEmail = lazy(() => import("./pages/UpdateEmail"));
const WebauthnError = lazy(() => import("./pages/WebauthnError"));
const DeleteCredential = lazy(() => import("./pages/DeleteCredential"));
const FrontchannelLogout = lazy(() => import("./pages/FrontchannelLogout"));
const IdpReviewUserProfile = lazy(() => import("./pages/IdpReviewUserProfile"));
const LoginIdpLinkConfirmOverride = lazy(
    () => import("./pages/LoginIdpLinkConfirmOverride")
);
const LoginRecoveryAuthnCodeConfig = lazy(
    () => import("./pages/LoginRecoveryAuthnCodeConfig")
);
const LoginRecoveryAuthnCodeInput = lazy(
    () => import("./pages/LoginRecoveryAuthnCodeInput")
);
const LoginOauth2DeviceVerifyUserCode = lazy(
    () => import("./pages/LoginOauth2DeviceVerifyUserCode")
);
const WebauthnAuthenticate = lazy(() => import("./pages/WebauthnAuthenticate"));
const WebauthnRegister = lazy(() => import("./pages/WebauthnRegister"));
const LoginConfigTotp = lazy(() => import("./pages/LoginConfigTotp"));
const LoginOauthGrant = lazy(() => import("./pages/LoginOauthGrant"));

const doMakeUserConfirmPassword = true;

function buildTheme(direction: "ltr" | "rtl") {
    return createTheme(createPamsThemeOptions("light", undefined, direction), {
        components: {
            MuiTypography: {
                styleOverrides: {
                    root: {
                        "&.kc-caption-info": {
                            lineHeight: 1.4,
                            display: "flex",
                            justifyContent: "flex-end",
                            marginTop: 12,
                            color: "#6b7280"
                        }
                    }
                }
            },
            MuiLinearProgress: {
                styleOverrides: {
                    root: { backgroundColor: "transparent" },
                    bar: { backgroundColor: "#0b5cad" }
                }
            },
            MuiListItemButton: {
                styleOverrides: {
                    root: {
                        backgroundColor: "#ffffff",
                        color: "#6b7280",
                        "&:hover, &.Mui-focusVisible, &:focus": {
                            color: "#1f2937",
                            backgroundColor: "#f8fafc"
                        }
                    }
                }
            },
            MuiListItemIcon: {
                styleOverrides: {
                    root: {
                        color: "#6b7280",
                        ".MuiListItemButton-root:hover &": { color: "#1f2937" }
                    }
                }
            },
            MuiPaper: {
                styleOverrides: {
                    root: {
                        "&.MuiMenu-paper": {
                            backgroundColor: "#ffffff",
                            border: "1px solid #e0e4ea",
                            boxShadow: "0 8px 24px rgba(16, 24, 40, 0.12)"
                        }
                    }
                }
            },
            MuiMenuItem: {
                styleOverrides: {
                    root: {
                        backgroundColor: "#ffffff",
                        color: "#6b7280",
                        "&:hover": { color: "#1f2937", backgroundColor: "#f8fafc" },
                        "&.Mui-selected": { color: "#0b5cad", backgroundColor: "#ffffff" }
                    }
                }
            },
            MuiList: {
                styleOverrides: {
                    root: {
                        "&.kcRecoveryCodesList": {
                            backgroundColor: "#f8fafc",
                            borderRadius: 8,
                            padding: 16
                        }
                    }
                }
            }
        }
    });
}

export default function KcPage(props: { kcContext: KcContext }) {
    const { kcContext } = props;

    const { i18n } = useI18n({ kcContext });
    const languageTag = String(i18n.currentLanguage.languageTag ?? "en").toLowerCase();
    const direction = languageTag.startsWith("ar") ? "rtl" : "ltr";

    const cache = useMemo(
        () =>
            createCache({
                key: direction === "rtl" ? "pams-rtl" : "pams",
                stylisPlugins: direction === "rtl" ? [prefixer, rtlPlugin] : [prefixer]
            }),
        [direction]
    );
    const theme = useMemo(() => buildTheme(direction), [direction]);

    useEffect(() => {
        document.documentElement.lang = languageTag;
        document.documentElement.dir = direction;
    }, [direction, languageTag]);

    return (
        <CacheProvider value={cache}>
            <ThemeProvider theme={theme}>
                <Suspense>
                    {(() => {
                        switch (kcContext.pageId) {
                            case "login.ftl":
                                return (
                                    <Login
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "register.ftl":
                                return (
                                    <Register
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                        UserProfileFormFields={UserProfileFormFields}
                                        doMakeUserConfirmPassword={
                                            doMakeUserConfirmPassword
                                        }
                                    />
                                );
                            case "login-username.ftl":
                                return (
                                    <LoginUsername
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-password.ftl":
                                return (
                                    <LoginPassword
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-reset-password.ftl":
                                return (
                                    <LoginResetPassword
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-passkeys-conditional-authenticate.ftl":
                                return (
                                    <LoginPasskeysConditionalAuthenticate
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-oauth2-device-verify-user-code.ftl":
                                return (
                                    <LoginOauth2DeviceVerifyUserCode
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-recovery-authn-code-input.ftl":
                                return (
                                    <LoginRecoveryAuthnCodeInput
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "terms.ftl":
                                return (
                                    <Terms
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "webauthn-authenticate.ftl":
                                return (
                                    <WebauthnAuthenticate
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "webauthn-register.ftl":
                                return (
                                    <WebauthnRegister
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "error.ftl":
                                return (
                                    <Error
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "code.ftl":
                                return (
                                    <Code
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "delete-account-confirm.ftl":
                                return (
                                    <DeleteAccountConfirm
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "select-authenticator.ftl":
                                return (
                                    <SelectAuthenticator
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-verify-email.ftl":
                                return (
                                    <LoginVerifyEmail
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-update-profile.ftl":
                                return (
                                    <LoginUpdateProfile
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                        UserProfileFormFields={UserProfileFormFields}
                                        doMakeUserConfirmPassword={
                                            doMakeUserConfirmPassword
                                        }
                                    />
                                );
                            case "login-update-password.ftl":
                                return (
                                    <LoginUpdatePassword
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-reset-otp.ftl":
                                return (
                                    <LoginResetOtp
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-page-expired.ftl":
                                return (
                                    <LoginPageExpired
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-otp.ftl":
                                return (
                                    <LoginOtp
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-oauth-grant.ftl":
                                return (
                                    <LoginOauthGrant
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-idp-link-email.ftl":
                                return (
                                    <LoginIdpLinkEmail
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-idp-link-confirm.ftl":
                                return (
                                    <LoginIdpLinkConfirm
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-config-totp.ftl":
                                return (
                                    <LoginConfigTotp
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "saml-post-form.ftl":
                                return (
                                    <SamlPostForm
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "logout-confirm.ftl":
                                return (
                                    <LogoutConfirm
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-x509-info.ftl":
                                return (
                                    <LoginX509Info
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "info.ftl":
                                return (
                                    <Info
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "update-email.ftl":
                                return (
                                    <UpdateEmail
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                        UserProfileFormFields={UserProfileFormFields}
                                        doMakeUserConfirmPassword={
                                            doMakeUserConfirmPassword
                                        }
                                    />
                                );
                            case "webauthn-error.ftl":
                                return (
                                    <WebauthnError
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "delete-credential.ftl":
                                return (
                                    <DeleteCredential
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "frontchannel-logout.ftl":
                                return (
                                    <FrontchannelLogout
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "idp-review-user-profile.ftl":
                                return (
                                    <IdpReviewUserProfile
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                        UserProfileFormFields={UserProfileFormFields}
                                        doMakeUserConfirmPassword={
                                            doMakeUserConfirmPassword
                                        }
                                    />
                                );
                            case "login-idp-link-confirm-override.ftl":
                                return (
                                    <LoginIdpLinkConfirmOverride
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );
                            case "login-recovery-authn-code-config.ftl":
                                return (
                                    <LoginRecoveryAuthnCodeConfig
                                        {...{ kcContext, i18n, classes }}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                    />
                                );

                            default:
                                return (
                                    <DefaultPage
                                        kcContext={kcContext}
                                        i18n={i18n}
                                        classes={classes}
                                        Template={Template}
                                        doUseDefaultCss={false}
                                        UserProfileFormFields={UserProfileFormFields}
                                        doMakeUserConfirmPassword={
                                            doMakeUserConfirmPassword
                                        }
                                    />
                                );
                        }
                    })()}
                </Suspense>
            </ThemeProvider>
        </CacheProvider>
    );
}

const classes = {} satisfies { [key in ClassKey]?: string };
