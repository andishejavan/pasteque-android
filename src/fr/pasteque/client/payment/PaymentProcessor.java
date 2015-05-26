package fr.pasteque.client.payment;

import android.content.Intent;
import fr.pasteque.client.Configure;
import fr.pasteque.client.fragments.PaymentFragment;
import fr.pasteque.client.models.Payment;

public abstract class PaymentProcessor {

	protected PaymentFragment paymentFragment;
	
	protected Payment payment;
	
	protected PaymentListener listener;
	
	public enum Status {
		VALIDATED,
		PENDING
	}

	protected PaymentProcessor (PaymentFragment parentActivity, PaymentListener listener, Payment payment) {
		this.paymentFragment = parentActivity;
		this.listener = listener;
		this.payment = payment;
	}

	public abstract void handleIntent(int requestCode, int resultCode,
            Intent data);

	public abstract Status initiatePayment();

	public static PaymentProcessor getProcessor(PaymentFragment parentActivity, PaymentListener listener, Payment payment) { 
		if ("magcard".equals(payment.getMode().getCode())) {
			if (Configure.getPayleven(parentActivity.getActivity()))
				return new PaylevenPaymentProcessor(parentActivity, listener, payment);
			else
				return new AtosPaymentProcessor(parentActivity, listener, payment);
		}
		return null;
	}
	
	public interface PaymentListener {
		abstract void registerPayment(Payment p);
	}
}
