package com.receiptbook.app.ui

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.receiptbook.app.AppContainer

private val authRoutes = setOf("login", "register", "verify/{email}", "forgot")

@Composable
fun AppNav(c: AppContainer) {
    val nav = rememberNavController()
    val email by c.session.email.collectAsStateWithLifecycle()
    val start = remember { if (c.session.email.value == null) "login" else "businesses" }

    // If the session disappears while inside the app, return to the login screen.
    LaunchedEffect(email) {
        val route = nav.currentDestination?.route
        if (email == null && route != null && route !in authRoutes) nav.navigate("login") { popUpTo(0) }
    }

    fun toBusinesses() = nav.navigate("businesses") { popUpTo(0) }
    fun s(e: androidx.navigation.NavBackStackEntry, k: String): String = e.arguments?.getString(k) ?: ""

    NavHost(nav, startDestination = start) {
        // ---- auth
        composable("login") {
            val vm = vm { AuthViewModel(c) }
            LoginScreen(vm, onSuccess = { toBusinesses() }, onRegister = { nav.navigate("register") },
                onForgot = { nav.navigate("forgot") }, onVerify = { nav.navigate("verify/${Uri.encode(it)}") },
                onLanguage = { nav.navigate("language") })
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
                onLanguage = { nav.navigate("language") },
                onLoggedOut = { nav.navigate("login") { popUpTo(0) } })
        }
        composable("language") { LanguageScreen(onBack = { nav.popBackStack() }) }
        composable("business/new") {
            BusinessFormScreen(c, null, onDone = { id -> if (id != null) nav.navigate("home/$id") { popUpTo("businesses") } else nav.popBackStack() }, onBack = { nav.popBackStack() })
        }
        composable("business/edit/{bid}") { e ->
            BusinessFormScreen(c, s(e, "bid"), onDone = { r -> if (r == "DELETED") nav.popBackStack("businesses", false) else nav.popBackStack() }, onBack = { nav.popBackStack() })
        }

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
            CustomerDetailScreen(c, bid, cid, onBack = { nav.popBackStack() }, onNewOrder = { nav.navigate("neworder/$bid?cid=$cid") })
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
