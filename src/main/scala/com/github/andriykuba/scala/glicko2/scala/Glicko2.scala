package com.github.andriykuba.scala.glicko2.scala

import scala.math._
import scala.annotation.tailrec


/**
 * Glicko2 rating calculation.
 * 
 * @see <a href="http://www.glicko.net/glicko/glicko2.pdf">Example of the Glicko-2 system</a>.
 * 
 * <p>
 * The Double calculations are not
 * precision and the paper has some inaccuracy in calculations in the part of
 * rounding and precision digits.
 * </p>
 * <br><br>In the paper:
 * 
 * <pre>{@code
 * r` = −0.2069(173.7178) + 1500 = 1464.06  
 * }</pre>
 * 
 * <br>In the case of Double calculation:
 *
 * <pre>{@code
 * r` = −0.2069(173.7178) + 1500 = 1464.05778718
 * }</pre>
 * 
 * <p>
 * It rounded to 1464.06 that is correct, but, if we use Double, we can receive result like this
 * </p>
 * 
 * <pre>{@code
 * r` = -0.20694097869815928 * (173.7178) + 1500 = 1464.05066845070891
 * }</pre>
 * 
 * <p>
 * This result could be rounded to 1464.05. 
 * </p>
 * 
 * <p>
 * The calculation in paper has classic error of rounding in the middle of calculation.
 * </p>
 * 
 * <pre>{@code
 * µ` = 0 + (0.8722 * 0.8722) × [0.9955(1 − 0.639) + 0.9531(0 − 0.432) + 0.7242(0 − 0.303)] 
 *    = 0 + 0.7607(−0.272) 
 *    = −0.2069
 * }</pre>
 * 
 * <p>
 * Let's do the same calculation with saving of all available middle precision
 * </p>
 * 
 * <pre>{@code
 * µ` = 0 + 0.76073284 * (0.9955 * 0.361) + (0.9531 * (-0.432)) + (0.7242 * (-0.303)) 
 *    = 0.76073284 * (0.3593755 - 0.4117392 - 0.2194326) 
 *    = 0.76073284 * (-0.2717963) 
 *    = -0.20676437120049
 * }</pre>
 * 
 * <p>
 * And then 
 * </p>
 * 
 * <pre>{@code
 * r` = -0.20676437120049 * (173.7178) + 1500 = 1464.08134831666752
 * }</pre>
 * 
 * <p>
 * That is rounded to 1464.08! Far away from the 1464.06. 
 * </p>
 * <p>
 * The reason to so different results is losing of precision. 
 * We use rounding in the calculation so we need to remember about precision.
 * In the paper µ` has precision 3 (multiplication of 0.361, -0.432 ... ), so result of 
 * </p>
 * 
 * <pre>{@code
 * −0.2069 * (173.7178) = -35.94221282 
 * </pre>}
 * 
 * <p>
 * has also precision 3, as well as the result of
 * </p>
 * 
 * <pre>{@code
 * -0.20676437120049 * (173.7178) = -35.91865168333248
 * </pre>}
 * 
 * <p>
 * It means that we can strictly believe only in one digit after decimal point.
 * Default rating, 1500, is the exact number, i.e. it has infinite precision: 1500.000000 ... Then
 * </p>
 * 
 * <pre>{@code
 * r` = -35.94221282 + 1500.000000 
 *    = 1464.05778718 
 *    = 1464.1 
 * </pre>}
 * 
 * <p>
 * So all our calculations is correct, but we can not write 1464.06, we must write 1464.1
 * Unfortunately, numbers in µ` calculation, like 0.9955, 0.361 was rounded as well. 
 * Usually, if it is not possible to not round in the middle of calculation, 
 * then rounded numbers must be computed with two more significant figures 
 * than the very final result. 
 * By this rule, final result of the  calculation that use µ` would have precision 1 
 * and the very strict answer is 1460. 
 * </p>
 * 
 * <p>
 * We see now, that the 1464.06 is not correct answer. 
 * Let's find the correct answer with the precision of 6 (2 digits after decimal point in our case)
 * </p>
 * 
 * <pre>{@code
 * µ` = 0.76073284 * (0.995498 * 0.360532) + (0.953149 * (-0.431842)) + (0.724235 * (-0.302841)) 
 *    = 0.76073284 * (0.358908884936 - 0.411609770458 - 0.219328051635) 
 *    = 0.76073284 * -0.272028937157 
 *    = -0.20694134592563
 * </pre>}
 * 
 * <pre>{@code
 * r` = -0.20694134592563 * 173.7178 + 1500 
 *    = -35.94939534323941 + 1500 
 *    = 1464.05060465676059 
 *    = 1464.05
 * </pre>}   
 * 
 * <p>   
 * The precision of -35.94939534323941 was 6 with 4 digits after decimal point. 
 * The 1500 is exact number, so addition of -35.94939534323941 and 1500 
 * will give precision 4 before decimal point and precision 4 after decimal point
 * like 1464.0506. It is the final answer so we will round off two last significant number 
 * to receive safe result. 
 *  
 * The correct answer is 1464.05.
 * </p>
 * 
 * <p>
 * The same with RD` calculation, fortunately it  matches a correct value
 * </p>
 * 
 * <p>
 * σ` is just truncated. The correct answer is 0.0599958431496. It's definitely not 0.05999.
 * If we want to round it to 6 digits after decimal point, then it would be 0.06000. 
 * It looks like better to use one more digit to represent this number, just to catch changes.
 * </p>
 * 
 * <pre>{@code
 * σ` = 0.059996
 * </pre>}
 * 
 */
object Glicko2 {  
  
  /**
   * Player data in Glicko rating system
   * 
   * BigDecimal used to simplification store player data in data storage
   * Rating and deviation scaled to 2 and volatility scaled to 6. 
   * 
   * BigDecimal is used to ensure that numbers are transmitted exactly.
   * Calculations are in Double. 
   * 
   * @see https://www.ibm.com/developerworks/library/j-jtp0114/
   * 
   * @param rating 				is "r" in the terms of the formula.
   * @param deviation 		is "RD" in the terms of the formula.
   * @param volatility		is "σ" in the terms of the formula.
   */
  case class Player(
      rating: BigDecimal, 
      deviation: BigDecimal, 
      volatility: BigDecimal){
    
    /**
     * Lowest value of 95% confidence interval
     */
    def ratingLow = rating - (2 * deviation)
    
    /**
     * Highest value of 95% confidence interval
     */
    def ratingHight = rating + (2 * deviation)
  }
  
  /**
   * Game that represents an opponent and the result against it.
   */
  trait Game{def opponent: Player}
  case class Win(val opponent: Player) extends Game
  case class Draw(val opponent: Player) extends Game
  case class Loss(val opponent: Player) extends Game
  
  /**
   * Scores against an opponent.
   */
  private def score(game: Game) = game match{
    case _: Win => 1
    case _: Draw => 0.5
    case _: Loss => 0
  }
    
  /**
   * Game parameters. Generally the same for all calculations. 
   * 
   * In the terms of formula description:
   * 
   * @param tau 											"τ" in the terms of the formula.
   * @param epsilon 									"ε"  in the terms of the formula.
   * @param scale 					  				"173.7178"  just a number in the terms of the formula.
   * @param defaultRating 						"r" of the new player in the terms of the formula.
   * @param defaultDeviation 					"RD" of the new player in the terms of the formula.
   * @param defaultSigma 							"σ" of the new player in the terms of the formula.
   * @param ratingScale								number of digits to the right of decimal point 
   * 																	in result rating.
   * @param deviationScaleratingScale	number of digits to the right of decimal point 
   * 																	in result deviation.
   * @param sigmaScaleratingScale			number of digits to the right of decimal point 
   * 																  in result scale.
   */
  case class Parameters(
    
    // Game constants.
    tau: Double = 0.5, 
    epsilon: Double = 0.000001,
    scale: Double = 173.7178,
    
    // Default values for the new player.
    defaultRating: Double = 1500,
    defaultDeviation: Double = 350,
    defaultVolatility: Double = 0.06,
    
    // Scale of the result.
    ratingScale: Int = 2,
    deviationScale: Int = 2,
    sigmaScale: Int = 6)
  
  /**
   * Calculate new rating of the player on the base of the set of games.
   * 
   * @param player			new rating would be calculated for this player.
   * @param games				set of games that player played in the rating period.
   * @param parameters	game parameters, like "τ", "ε" and so on.
   * 										Default {@link Parameters Parameters} is used if none passed
   * 
   * @return						new player object with the new rating. {@link #calculateEmptyPeriod(Player)
   * calculateEmptyPeriod} is called in the case of the empty {@code games}.  
   * Rating scale according to the calculation parameters.
   */
  def update(player:Player, games:Seq[Game] = Seq.empty)
    (implicit parameters: Parameters = Parameters()) = {
    
    if(games.isEmpty) {
      updateWithEmptyPeriod(player)
    }else{
      // There are some calculations that used in more than one place
      // It was optimized to calculate only one, 
      // so calculation code is not work step by step like in the paper. 
     
      val player2 = Converter.glicko2(player)
     
      val preVAndDelta = games.foldLeft(0D, 0D)({
        case (sums, game) => {
          val opponent = Converter.glicko2Opponent(player2, game)
          (sums._1 + (pow(opponent.gphi, 2) * opponent.E * (1 - opponent.E)),
          (sums._2 + (opponent.gphi * (opponent.s - opponent.E))))
      }})
      
      val v = pow(preVAndDelta._1, -1)
      val preDelta = preVAndDelta._2
      val delta = v * preDelta

      val phi2 = pow(player2.phi, 2)
      val dSigma = Formulas.dSigma(player2, delta, v, phi2)
      
      val tPhi = sqrt(phi2 + pow(dSigma, 2))

      val dPhi = 1/sqrt(1/pow(tPhi, 2) + 1/v)
      
      val dMu = player2.mu + pow(dPhi, 2) * preDelta

      Converter.glicko(dMu, dPhi, dSigma)
    }
  }
  
  /**
   * Calculate new rating of the player that 
   * does not compete during the rating period.
   * 
   * @param player 		new rating would be calculated for this player.
   * @param parameters	game parameters, like "τ", "ε" and so on.
   * 										Default {@link Parameters Parameters} is used if none passed
   * 
   * @return					new player object with the new rating.
   */
  private def updateWithEmptyPeriod(player:Player)
    (implicit parameters: Parameters = Parameters()) = {
    
    val player2 = Converter.glicko2(player)
    val dPhi = sqrt(pow(player2.phi, 2) + pow(player2.sigma, 2))
    Converter.glicko(player2.mu, dPhi, player2.sigma) 
  }
  
  /**
   * Create new Player with the default Glicko rating
   * 
	 * @param parameters	game parameters. 
	 * 										Default {@link Parameters Parameters} is used if none passed.
   */
  def defaultPlayer()
    (implicit parameters: Parameters = Parameters()) = 
      Player(
          rating = parameters.defaultRating,
          deviation = parameters.defaultDeviation,
          volatility = parameters.defaultVolatility)
  
  /**
   * Player scaled to Glicko2 rating system
   * @param mu				is "µ" in the terms of the formula.
   * @param phi				is "φ" in the terms of the formula.
   * @param sigma			is "σ" in the terms of the formula.
   */
  private case class Scaled(mu: Double, phi: Double, sigma: Double)

  /**
   * Oponent's data that is re-used.
   * 
   * @param opponent  opponent of the player
   * @param gphi  		is "g(φ)" in the terms of the formula.
   * @param E					is "E" in the terms of the formula.
   * @param s					is "s" in the terms of the formula.
   */
  private case class Opponent(opponent: Scaled, gphi: Double, E:Double, s:Double)
  
  /**
   * Converter between Glicko and Glicko2 ratings
   */
  private object Converter{
    
    /**
     * Convert to the Glicko2 rating from the Glicko
     * 
     * @param player		player in terms of Glicko rating system
     */
    def glicko2(player: Player)
      (implicit parameters: Parameters) = 
      Scaled(
        mu = (player.rating.toDouble - parameters.defaultRating)/parameters.scale,
        phi = player.deviation.toDouble/parameters.scale,
        sigma = player.volatility.toDouble
      )

    /**
     * Convert to the Glicko2 player data to player of Glicko rating system.
     * 
	   * @param mu				is "µ" in the terms of the formula.
  	 * @param phi				is "φ" in the terms of the formula.
   	 * @param sigma	  	is "σ" in the terms of the formula.
   	 * 
   	 * @return 					{@link Player Player} with the rating scaled according to the 
   	 * 									calculation parameters
     */
    def glicko(mu: Double, phi: Double, sigma: Double)
      (implicit parameters: Parameters) = Player(
          
      rating = scale(parameters.scale * mu + parameters.defaultRating, parameters.ratingScale), 
      deviation = scale(parameters.scale * phi, parameters.deviationScale), 
      volatility = scale(sigma, parameters.sigmaScale)
    )
    
    /**
     * Scale number, i.e. round double to some number of decimal digits
     * 
     * @param number		number to be scaled
     * @param places		number of digits to the right of decimal point in result number
     */
    private def scale(number: Double, places:Int) = 
      BigDecimal(number).setScale(places, BigDecimal.RoundingMode.HALF_UP)
    
    /**
     * Convert opponent from the {@link Game} to the Glicko2 scale and calculate 
     * "g(φ)" and "E" for it.
     * 
     * @param player	Player, whose rating is calculated. 
     * @param game		Game data - opponent and game result.
     */
    def glicko2Opponent(player: Scaled, game: Game)
      (implicit parameters: Parameters) ={
      
      val opponent = Converter.glicko2(game.opponent)
      val gphi = Formulas.g(opponent)
      val E = Formulas.E(player, opponent, gphi)
      Opponent(opponent, gphi, E, score(game))
    }
  }
    
  /**
   * Formulas of the calculation
   */
  private object Formulas{
    // There are some calculations that used in more than one place
    // It was optimized to calculate only one, 
    // so calculation code is not work step by step like in the paper.
    
    val pi2 = pow(Pi, 2)
  
    def g(player: Scaled) = 
      1 / (sqrt(1 + (3 * pow(player.phi, 2)) / pi2))
    
    def E(player: Scaled, opponent: Scaled, gphi: Double) = 
      1/(1 + exp(- gphi * (player.mu - opponent.mu)))
  
    def dSigma(player: Scaled, delta: Double, v:Double, phi2: Double)
      (implicit parameters: Parameters)  = {
      
      val a = log(pow(player.sigma, 2))
       
      // f calculation
      val p2 = pow(delta, 2)
      val p3 = p2 - phi2 - v
      val p4 = phi2 + v
      val p5 = pow(parameters.tau, 2)
      
      def f(x:Double) = {
        val eX = exp(x)
        ((eX * (p3 - eX))/(2 * pow(p4 + eX, 2))) - ((x-a)/p5)
      }
      
      @tailrec
      def findB(k: Int): Double = {
        val B = a - k * parameters.tau
        val fB = f(B)
        if(fB>=0) B else findB(k+1)
      }
      
      @tailrec
      def findSigma(A: Double, B:Double, fA:Double, fB:Double): Double = {
        if(abs(B-A) <= parameters.epsilon) {
          exp(A/2)
        } else {
          val C = A + (A - B) * fA/(fB - fA)
          val fC = f(C)
          if(fC * fB < 0){
            findSigma(B, C, fB, fC)   
          }else{
            findSigma(A, C, fA/2, fC)
          }
        }
      }
      
      val A = a
      val B = if(p2 > p4) log(p3) else findB(1)
  
      findSigma(A, B, f(A), f(B))
    }
  }
  

}