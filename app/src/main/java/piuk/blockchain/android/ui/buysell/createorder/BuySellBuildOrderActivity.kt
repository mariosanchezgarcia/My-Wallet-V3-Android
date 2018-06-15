package piuk.blockchain.android.ui.buysell.createorder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.constraint.ConstraintSet
import android.support.design.widget.CoordinatorLayout
import android.support.transition.AutoTransition
import android.support.transition.TransitionManager
import android.support.v7.app.AlertDialog
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.*
import kotlinx.android.synthetic.main.toolbar_general.toolbar_general
import piuk.blockchain.android.R
import piuk.blockchain.android.injection.Injector
import piuk.blockchain.android.ui.buysell.confirmation.CoinifyOrderConfirmationActivity
import piuk.blockchain.android.ui.buysell.createorder.models.ConfirmationDisplay
import piuk.blockchain.android.ui.buysell.createorder.models.OrderType
import piuk.blockchain.android.ui.chooser.AccountChooserActivity
import piuk.blockchain.android.ui.chooser.AccountMode
import piuk.blockchain.android.util.extensions.MemorySafeSubscription
import piuk.blockchain.android.util.extensions.addToCompositeDisposable
import piuk.blockchain.androidcore.data.currency.toSafeDouble
import piuk.blockchain.androidcore.utils.extensions.toKotlinObject
import piuk.blockchain.androidcore.utils.helperfunctions.consume
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import piuk.blockchain.androidcoreui.ui.base.BaseMvpActivity
import piuk.blockchain.androidcoreui.ui.customviews.MaterialProgressDialog
import piuk.blockchain.androidcoreui.ui.customviews.NumericKeyboardCallback
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom
import piuk.blockchain.androidcoreui.utils.extensions.disableSoftKeyboard
import piuk.blockchain.androidcoreui.utils.extensions.getResolvedColor
import piuk.blockchain.androidcoreui.utils.extensions.gone
import piuk.blockchain.androidcoreui.utils.extensions.invisible
import piuk.blockchain.androidcoreui.utils.extensions.invisibleIf
import piuk.blockchain.androidcoreui.utils.extensions.toast
import piuk.blockchain.androidcoreui.utils.extensions.visible
import piuk.blockchain.androidcoreui.utils.helperfunctions.onItemSelectedListener
import timber.log.Timber
import java.text.DecimalFormatSymbols
import java.util.*
import javax.inject.Inject
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.button_review_order as buttonReviewOrder
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.buysell_constraint_layout as constraintLayout
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.edit_text_receive_amount as editTextReceive
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.edit_text_send_amount as editTextSend
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.progress_bar_quote_rate as progressBarQuote
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.spinner_account_selection as spinnerAccountSelection
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.spinner_currency_selection as spinnerCurrencySelection
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.text_view_account_to_from as textViewAccountTitle
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.text_view_account_to_from_description as textViewAccountDescription
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.text_view_limits as textViewLimits
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.text_view_quote_price as textViewQuotePrice
import kotlinx.android.synthetic.main.activity_buy_sell_build_order.view_divider_quote as viewQuoteDivider

class BuySellBuildOrderActivity :
    BaseMvpActivity<BuySellBuildOrderView, BuySellBuildOrderPresenter>(), BuySellBuildOrderView,
    MemorySafeSubscription,
    NumericKeyboardCallback {

    @Inject lateinit var presenter: BuySellBuildOrderPresenter
    override val locale: Locale = Locale.getDefault()
    override val compositeDisposable = CompositeDisposable()
    override val orderType by unsafeLazy { intent.getSerializableExtra(EXTRA_ORDER_TYPE) as OrderType }
    private var progressDialog: MaterialProgressDialog? = null
    private val defaultDecimalSeparator =
            DecimalFormatSymbols.getInstance().decimalSeparator.toString()
    private val editTexts by unsafeLazy {
        listOf(editTextSend, editTextReceive)
    }
    private val accountSelectorViews by unsafeLazy {
        listOf(spinnerAccountSelection, textViewAccountTitle, textViewAccountDescription)
    }
    private val accountSelectionClickable by unsafeLazy {
        listOf(spinnerAccountSelection, textViewAccountDescription)
    }

    init {
        Injector.INSTANCE.presenterComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buy_sell_build_order)
        require(intent.hasExtra(EXTRA_ORDER_TYPE)) { "You must pass an order type to the Activity. Please start this Activity via the provided static factory method." }

        val (title, label) = when (orderType) {
            OrderType.Buy, OrderType.BuyCard -> R.string.buy_sell_buy to R.string.to
            OrderType.Sell -> R.string.buy_sell_sell to R.string.from
        }

        setupToolbar(toolbar_general, title)
        textViewAccountTitle.setText(label)

        val sendObservable = getTextWatcherObservable(editTextSend, presenter.sendSubject)
        val receiveObservable = getTextWatcherObservable(editTextReceive, presenter.receiveSubject)

        sendObservable
                .onErrorResumeNext(sendObservable)
                .addToCompositeDisposable(this)
                .subscribe()

        receiveObservable
                .onErrorResumeNext(receiveObservable)
                .addToCompositeDisposable(this)
                .subscribe()

        setupKeypad()

        textViewLimits.setOnClickListener { presenter.onMaxClicked() }
        buttonReviewOrder.setOnClickListener { presenter.onConfirmClicked() }

        onViewReady()
    }

    override fun renderExchangeRate(status: BuySellBuildOrderPresenter.ExchangeRateStatus) {
        when (status) {
            is BuySellBuildOrderPresenter.ExchangeRateStatus.Data -> renderExchangeRateData(status)
            BuySellBuildOrderPresenter.ExchangeRateStatus.Loading -> renderExchangeRateLoading()
            BuySellBuildOrderPresenter.ExchangeRateStatus.Failed -> renderExchangeRateFailure()
        }
        clearEditTexts()
    }

    override fun startOrderConfirmation(orderType: OrderType, quote: ConfirmationDisplay) {
        CoinifyOrderConfirmationActivity.startForResult(
                this,
                CoinifyOrderConfirmationActivity.REQUEST_CODE_CONFIRM_ORDER,
                orderType,
                quote
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CHOOSE_ACCOUNT && resultCode == Activity.RESULT_OK) {
            handleRequestResult(data!!)
        } else if (requestCode == CoinifyOrderConfirmationActivity.REQUEST_CODE_CONFIRM_ORDER
            && resultCode == Activity.RESULT_OK
        ) {
            // If CoinifyOrderConfirmationActivity finishes with no issues, clear this page too
            finish()
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getTextWatcherObservable(
            editText: EditText,
            publishSubject: PublishSubject<String>
    ): Observable<String> = RxTextView.textChanges(editText)
            // Logging
            .doOnError(Timber::e)
            .doOnTerminate { Timber.wtf("Text watcher terminated unexpectedly $editText") }
            // Skip first event emitted when subscribing
            .skip(1)
            // Convert to String
            .map { it.toString() }
            // Ignore elements emitted by non-user events (ie presenter updates) and those
            // emitted from changes to paired EditText (ie edit fiat, edit crypto)
            .doOnNext { if (currentFocus == editText) publishSubject.onNext(it) }
            // Scheduling
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())

    private fun renderExchangeRateFailure() {
        textViewQuotePrice.invisible()
        showQuoteInProgress(false)
        toast(R.string.buy_sell_error_fetching_quote, ToastCustom.TYPE_ERROR)
    }

    private fun renderExchangeRateData(status: BuySellBuildOrderPresenter.ExchangeRateStatus.Data) {
        textViewQuotePrice.text = status.formattedQuote
        textViewQuotePrice.visible()
        showQuoteInProgress(false)
    }

    private fun renderExchangeRateLoading() {
        textViewQuotePrice.invisible()
        showQuoteInProgress(true)
    }

    override fun renderSpinnerStatus(status: BuySellBuildOrderPresenter.SpinnerStatus) {
        when (status) {
            is BuySellBuildOrderPresenter.SpinnerStatus.Data -> setupSpinner(status.currencies)
            BuySellBuildOrderPresenter.SpinnerStatus.Loading -> showProgressDialog()
            BuySellBuildOrderPresenter.SpinnerStatus.Failure -> renderFailure(R.string.buy_sell_error_fetching_quote)
        }
    }

    override fun renderLimitStatus(status: BuySellBuildOrderPresenter.LimitStatus) {
        when (status) {
            is BuySellBuildOrderPresenter.LimitStatus.Data -> renderBuyLimitData(status)
            is BuySellBuildOrderPresenter.LimitStatus.ErrorData -> renderAmountTooLow(status)
            BuySellBuildOrderPresenter.LimitStatus.Loading -> showProgressDialog()
            BuySellBuildOrderPresenter.LimitStatus.Failure -> renderFailure(R.string.buy_sell_error_fetching_limit)
        }
    }

    private fun renderAmountTooLow(status: BuySellBuildOrderPresenter.LimitStatus.ErrorData) {
        val limit = status.limit
        val text = resources.getString(status.textResourceId, limit)

        with(textViewLimits) {
            // Clear old values
            this.text = null
            setText(text)
            setTextColor(getResolvedColor(R.color.secondary_red_light))
            setOnClickListener(null)
        }

        dismissProgressDialog()
    }

    private fun renderBuyLimitData(status: BuySellBuildOrderPresenter.LimitStatus.Data) {
        val limit = status.limit
        val text = resources.getString(status.textResourceId, limit)
        val start = text.indexOf(limit).coerceAtLeast(0)
        val end = start + limit.length

        // Clear old values
        textViewLimits.text = null
        val spannable = getFormattedLimit(status)

        spannable.setSpan(
                ForegroundColorSpan(getResolvedColor(R.color.primary_blue_accent)),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        textViewLimits.setText(spannable, TextView.BufferType.SPANNABLE)

        textViewLimits.setOnClickListener {
            val parsed = limit.toSafeDouble(locale)
            if (view.orderType == OrderType.Sell) {
                editTextReceive.requestFocus()
                editTextReceive.setText("$parsed")
            } else {
                editTextSend.requestFocus()
                editTextSend.setText("$parsed")
            }
        }

        dismissProgressDialog()
    }

    override fun displayAccountSelector(label: String) {
        updateAccountSelector(label)
        accountSelectorViews.forEach {
            it.visible()
            AlphaAnimation(0.0f, 1.0f).apply {
                duration = 300
                startOffset = 300
                fillAfter = true
            }.run { it.startAnimation(this) }
        }

        val transition = AutoTransition().apply { duration = 300 }
        TransitionManager.beginDelayedTransition(constraintLayout, transition)
        ConstraintSet().apply {
            clone(constraintLayout)
            connect(
                    R.id.view_divider_quote,
                    ConstraintSet.TOP,
                    R.id.spinner_account_selection,
                    ConstraintSet.BOTTOM
            )
            applyTo(constraintLayout)
        }

        accountSelectionClickable.forEach {
            it.setOnClickListener {
                AccountChooserActivity.startForResult(
                        this,
                        AccountMode.BitcoinHdOnly,
                        REQUEST_CODE_CHOOSE_ACCOUNT,
                        when (orderType) {
                            OrderType.Buy, OrderType.BuyCard -> getString(R.string.from)
                            OrderType.Sell -> getString(R.string.to)
                        }
                )
            }
        }
    }

    override fun updateAccountSelector(label: String) {
        textViewAccountDescription.text = label
    }

    @SuppressLint("SyntheticAccessor")
    private fun handleRequestResult(data: Intent) {
        presenter.account =
                data.getStringExtra(AccountChooserActivity.EXTRA_SELECTED_ITEM).toKotlinObject()
    }

    private fun getFormattedLimit(status: BuySellBuildOrderPresenter.LimitStatus.Data): SpannableString {
        val limit = status.limit
        val text = resources.getString(status.textResourceId, limit)
        return SpannableString(text)
    }

    override fun updateReceiveAmount(amount: String) {
        editTextReceive.setText(amount)
        editTextReceive.setSelection(amount.length)
    }

    override fun updateSendAmount(amount: String) {
        editTextSend.setText(amount)
        editTextSend.setSelection(amount.length)
    }

    override fun setButtonEnabled(enabled: Boolean) {
        buttonReviewOrder.isEnabled = enabled
    }

    override fun clearEditTexts() {
        editTextSend.text.clear()
        editTextReceive.text.clear()
    }

    override fun showToast(message: Int, toastType: String) {
        toast(message, toastType)
    }

    override fun showQuoteInProgress(inProgress: Boolean) {
        progressBarQuote.invisibleIf { !inProgress }
    }

    override fun onFatalError() {
        toast(R.string.unexpected_error, ToastCustom.TYPE_ERROR)
        finish()
    }

    private fun setupSpinner(currencies: List<String>) {
        val dataAdapter = ArrayAdapter<String>(this, R.layout.item_spinner_buy_sell, currencies)
                .apply { setDropDownViewResource(R.layout.item_spinner_buy_sell_list) }

        with(spinnerCurrencySelection) {
            adapter = dataAdapter
            setSelection(0, false)
            onItemSelectedListener = onItemSelectedListener {
                presenter.selectedCurrency = currencies[it]
            }
        }
        dismissProgressDialog()
    }

    private fun renderFailure(@StringRes message: Int) {
        dismissProgressDialog()
        toast(message, ToastCustom.TYPE_ERROR)
        finish()
    }

    override fun displayFatalErrorDialog(formattedString: String) {
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
                .setTitle(R.string.app_name)
                .setMessage(formattedString)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setCancelable(false)
                .show()
    }

    override fun showProgressDialog() {
        if (!isFinishing) {
            dismissProgressDialog()
            progressDialog = MaterialProgressDialog(this).apply {
                setMessage(getString(R.string.please_wait))
                setCancelable(false)
                show()
            }
        }
    }

    override fun dismissProgressDialog() {
        if (progressDialog?.isShowing == true) {
            progressDialog!!.dismiss()
            progressDialog = null
        }
    }

    override fun onSupportNavigateUp(): Boolean = consume { onBackPressed() }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    private fun isKeyboardVisible(): Boolean = buysell_keyboard.isVisible

    override fun onBackPressed() {
        if (isKeyboardVisible()) {
            closeKeyPad()
        } else {
            super.onBackPressed()
        }
    }

    private fun closeKeyPad() {
        buysell_keyboard.setNumpadVisibility(View.GONE)
    }

    private fun setupKeypad() {
        editTexts.forEach {
            it.disableSoftKeyboard()
            buysell_keyboard.enableOnView(it)
        }
        buysell_keyboard.apply {
            setDecimalSeparator(defaultDecimalSeparator)
            setCallback(this@BuySellBuildOrderActivity)
        }
    }

    override fun onKeypadClose() {
        val height = resources.getDimension(R.dimen.action_bar_height).toInt()
        // Resize activity to default
        buysell_scrollview.apply {
            setPadding(0, 0, 0, 0)
            layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(0, height, 0, 0) }

            postDelayed({ smoothScrollTo(0, 0) }, 100)
        }

        buttonReviewOrder.visible()
    }

    override fun onKeypadOpen() = Unit

    override fun onKeypadOpenCompleted() {
        // Resize activity around view
        val height = resources.getDimension(R.dimen.action_bar_height).toInt()
        buysell_scrollview.apply {
            setPadding(0, 0, 0, buysell_keyboard.height)
            layoutParams = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(0, height, 0, 0) }

            scrollTo(0, bottom)
        }

        buttonReviewOrder.gone()
    }

    override fun createPresenter(): BuySellBuildOrderPresenter = presenter

    override fun getView(): BuySellBuildOrderView = this

    companion object {

        private const val EXTRA_ORDER_TYPE =
                "piuk.blockchain.android.ui.buysell.payment.EXTRA_ORDER_TYPE"
        private const val REQUEST_CODE_CHOOSE_ACCOUNT = 1001

        fun start(context: Context, orderType: OrderType) {
            Intent(context, BuySellBuildOrderActivity::class.java)
                    .apply { putExtra(EXTRA_ORDER_TYPE, orderType) }
                    .run { context.startActivity(this) }
        }

    }

}