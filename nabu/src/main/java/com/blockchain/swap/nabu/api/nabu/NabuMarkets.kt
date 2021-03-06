package com.blockchain.swap.nabu.api.nabu

import com.blockchain.swap.nabu.api.trade.TradeFailureJson
import com.blockchain.swap.nabu.api.trade.TradeJson
import com.blockchain.swap.nabu.api.trade.TradeRequest
import com.blockchain.swap.nabu.api.trade.TradesLimits
import com.blockchain.swap.nabu.api.trade.TradingConfig
import io.reactivex.Completable
import io.reactivex.Single
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

internal interface NabuMarkets {

    @GET("markets/quotes/{trading_pair}/config")
    fun getTradingConfig(
        @Path("trading_pair") tradingPair: String,
        @Header("authorization") authorization: String
    ): Single<TradingConfig>

    @GET("trades/limits")
    fun getTradesLimits(
        @Query("currency") fiatCurrency: String,
        @Header("authorization") authorization: String
    ): Single<TradesLimits>

    @POST("trades")
    fun executeTrade(
        @Body tradeRequest: TradeRequest,
        @Header("authorization") authorization: String
    ): Single<TradeJson>

    @GET("trades")
    fun getTrades(
        @Query("userFiatCurrency") userFiatCurrency: String,
        @Header("authorization") authorization: String
    ): Single<List<TradeJson>>

    @PUT("trades/{id}/failure-reason")
    fun putTradeFailureReason(
        @Path("id") tradeId: String,
        @Body tradeFailureJson: TradeFailureJson,
        @Header("authorization") authorization: String
    ): Completable
}
