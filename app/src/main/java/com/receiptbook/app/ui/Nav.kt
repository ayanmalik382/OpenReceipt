package com.receiptbook.app.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.receiptbook.app.AppContainer

/**
 * Offline-first: the app always opens straight into the businesses list, fully usable with no
 * account. "login" is only ever reached by an explicit tap ("Sign in to back up"), so popping
 * back from it and "continue without an account" are the same action - both just return to
 * whatever the person was doing, now still signed out.
 */
@Composable
fun AppNav(c: AppContainer) {
    val nav = rememberNavController()

    fun toBusinesses() = nav.navigate("businesses") { popUpTo(0) }
    fun s(e: androidx.navigation.NavBackStackEntry, k: String): String = e.arguments?.getString(k) ?: ""

    NavHost(nav, startDestination = "businesses") {
        // ---- auth (opt-in only, for cloud backup)
        composable("login") {
            val vm = vm { AuthViewModel(c) }
            LoginScreen(vm, onSuccess = { nav.popBackStack() }, onRegister = { nav.navigate("register") },
                onForgot = { nav.navigate("forgot") }, onVerify = { nav.navigate("verify/${Uri.encode(it)}") },
                onLanguage = { nav.navigate("language") }, onContinueOffline = { nav.popBackStack() })
        }
        composable("register") {
            val vm = vm { AuthViewModel(c) }
            RegisterScreen(vm, onCodeSent = { nav.navigate("verify/${Uri.encode(it)}") }, onBack = { nav.popBackStack() })
        }
        composable("verify/{email}", arguments = listOf(navArgument("email") { type = NavType.StringType })) { e ->
            val vm = vm { AuthViewModel(c) }
            VerifyScreen(s(e, "email"), vm, onSuccess = { toBusinesses() })
        }
        composable("forgot") {
            val vm = vm { AuthViewModel(c) }
            ForgotScreen(vm, onSuccess = { toBusinesses() }, onBack = { nav.popBackStack() })
        }

        // ---- businesses
        composable("businesses") {
            BusinessListScreen(c, onOpen = { nav.navigate("home/$it") }, onNew = { nav.navigate("business/new") },
                onLanguage = { nav.navigate("language") }, onSignIn = { nav.navigate("login") },
                onAppIcon = { nav.navigate("appicon") })
        }
        composable("language") { LanguageScreen(onBack = { nav.popBackStack() }) }
        composable("appicon") { AppIconScreen(onBack = { nav.popBackStack() }) }
        composable("business/new") {
            BusinessFormScreen(c, null, onDone = { id -> if (id != null) nav.navigate("home/$id") { popUpTo("businesses") } else nav.popBackStack() },
                onBack = { nav.popBackStack() }, onTemplate = {})
        }
        composable("business/edit/{bid}") { e ->
            BusinessFormScreen(c, s(e, "bid"), onDone = { r -> if (r == "DELETED") nav.popBackStack("businesses", false) else nav.popBackStack() },
                onBack = { nav.popBackStack() }, onTemplate = { bid -> nav.navigate("business/template/$bid") })
        }
        composable("business/template/{bid}") { e -> TemplateScreen(c, s(e, "bid"), onBack = { nav.popBackStack() }) }

        // ---- inside a business
        composable("home/{bid}") { e -> HomeScreen(c, s(e, "bid"), onBack = { nav.popBackStack() }, go = { nav.navigate(it) }) }
        composable("products/{bid}") { e -> ProductsScreen(c, s(e, "bid"), onBack = { nav.popBackStack() }) }
        composable("suppliers/{bid}") { e -> SuppliersScreen(c, s(e, "bid"), onBack = { nav.popBackStack() }) }
        composable("customers/{bid}") { e ->
            val bid = s(e, "bid")
            CustomersScreen(c, bid, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("customer/$bid/$it") })
        }
        composable("customer/{bid}/{cid}") { e ->
            val bid = s(e, "bid"); val cid = s(e, "cid")
            CustomerDetailScreen(c, bid, cid, onBack = { nav.popBackStack() }, onNewOrder = { nav.navigate("neworder/$bid?cid=$cid") },
                onOpenOrder = { nav.navigate("order/$bid/$it") })
        }
        composable(
            "neworder/{bid}?cid={cid}",
            arguments = listOf(navArgument("cid") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { e ->
            val bid = s(e, "bid")
            NewOrderScreen(c, bid, e.arguments?.getString("cid"), onBack = { nav.popBackStack() },
                onSaved = { oid -> nav.navigate("order/$bid/$oid") { popUpTo("home/$bid") } })
        }
        composable("orders/{bid}") { e ->
            val bid = s(e, "bid")
            OrdersScreen(c, bid, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("order/$bid/$it") })
        }
        composable("order/{bid}/{oid}") { e -> OrderDetailScreen(c, s(e, "bid"), s(e, "oid"), onBack = { nav.popBackStack() }) }
        composable("cash/{bid}") { e -> CashScreen(c, s(e, "bid"), onBack = { nav.popBackStack() }) }
        composable("reports/{bid}") { e -> ReportsScreen(c, s(e, "bid"), onBack = { nav.popBackStack() }) }
    }
}
